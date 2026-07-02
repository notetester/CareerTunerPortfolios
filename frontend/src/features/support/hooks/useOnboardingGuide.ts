import { useCallback, useRef, useState } from "react";

import { runStream } from "@/features/autoprep/api/autoPrepApi";
import type { AutoPrepRequest, PrepStepResult } from "@/features/autoprep/types/autoPrep";
import {
  createCaseFromFile, createCaseFromText, createCaseFromUrl, fetchGithubReadme, getLatestExtractionStatus,
  retryExtraction, uploadDocument, type UploadedFile,
} from "../api/onboardingApi";
import {
  getField, githubRepoLabel, GITHUB_README_ERROR_COPY, GITHUB_README_ERROR_FALLBACK,
  type GuideStep, type LinkKey,
} from "../onboarding/guideData";

/** 불러온 GitHub README — 칩 표시(레포명+글자수)와 collect() 스냅샷에 함께 쓴다. */
export interface PortfolioReadme {
  repoLabel: string;
  text: string;
}

/** 업로드 진행 중인 서류 항목(낙관적 칩 — AutoPrepLauncher 패턴 차용). */
export interface DocItem {
  slot: "cover" | "resume" | "portfolio";
  kind: "RESUME" | "PORTFOLIO" | "ATTACHMENT";
  file: File;
  id?: number;
  uploading: boolean;
  error?: boolean;
}

/** 공고 입력(URL/붙여넣기/파일 중 하나). 파일은 즉시 업로드하지 않고 실행 시 "지원 건"으로 만든다. */
export interface JdState {
  url: string;
  text: string;
  file?: File;
}

/** 실행(오케 SSE) 결과 — 실제 part-done 이벤트로만 채운다(지어낸 값 없음). */
export interface GuideResult {
  fitScore: number | null; // FIT 이 실제로 돈 경우에만(없으면 null → 점수 표시 안 함)
  strengths: string[]; // FIT.matchedSkills → 없으면 PROFILE.strengths
  gaps: string[]; // FIT.missingSkills → 없으면 PROFILE.gaps
  written: boolean; // WRITE(자소서 교정) 완료 여부
  ranParts: string[]; // 실제로 DONE 된 파트 키
  fitPending: boolean; // 케이스는 있으나 FIT 이 아직(비동기 추출 대기)
}

/** "역량/스킬" 문자열(콤마·JSON 등) → 칩 배열(방어적 파싱, 최대 6개). */
function splitSkills(s?: string | null): string[] {
  if (!s) return [];
  const t = s.trim();
  if (!t) return [];
  try {
    const j = JSON.parse(t);
    if (Array.isArray(j)) return j.map((x) => String(x).trim()).filter(Boolean).slice(0, 6);
  } catch {
    /* not json */
  }
  return t.split(/[,\n·|/]/).map((x) => x.trim()).filter(Boolean).slice(0, 6);
}

/**
 * 온보딩 가이드 상태 + 실제 오케스트레이터 배선.
 * - 자소서(ATTACHMENT) → attachmentFileIds 로 오케에 실음(WRITE 가 소비).
 * - 이력서(RESUME)·포폴(PORTFOLIO) → 업로드해 fileId 는 보관하되 오케로 보내지 않음(소비 핸들러 없음).
 *   프로필/케이스 반영은 향후 별도 작업(아래 collect()/TODO).
 * - 공고 → from-job-posting 로 "지원 건" 생성(applicationCaseId) → FIT/JOB 이 이 케이스를 분석.
 */
export function useOnboardingGuide(initialStep: GuideStep = "role") {
  const [step, setStep] = useState<GuideStep>(initialStep);
  const [role, setRoleState] = useState<string | null>(null);
  const [customRole, setCustomRole] = useState("");
  const [skills, setSkills] = useState<string[]>([]);
  const [docs, setDocs] = useState<DocItem[]>([]);
  const [jd, setJd] = useState<JdState>({ url: "", text: "" });
  const [links, setLinks] = useState<Partial<Record<LinkKey, string>>>({});
  const [portfolioReadme, setPortfolioReadme] = useState<PortfolioReadme | null>(null);
  const [portfolioReadmeLoading, setPortfolioReadmeLoading] = useState(false);
  const [portfolioReadmeError, setPortfolioReadmeError] = useState<string | null>(null);
  const [caseId, setCaseId] = useState<number | null>(null);
  const [fit, setFit] = useState<GuideResult | null>(null);
  const [runError, setRunError] = useState<string | null>(null);

  const abortRef = useRef<AbortController>();
  const field = getField(role);

  const setRole = useCallback((r: string) => {
    setRoleState(r);
    setSkills([]);
  }, []);

  const toggleSkill = useCallback((s: string) => {
    setSkills((prev) => (prev.includes(s) ? prev.filter((x) => x !== s) : [...prev, s]));
  }, []);

  const setLink = useCallback((key: LinkKey, value: string) => {
    setLinks((prev) => ({ ...prev, [key]: value }));
  }, []);

  // ── GitHub 링크 → README 원문 불러오기(가이드 포폴 스텝) ──
  const fetchPortfolioReadme = useCallback(async () => {
    const url = (links.github ?? "").trim();
    if (!url) return;
    setPortfolioReadmeLoading(true);
    setPortfolioReadmeError(null);
    try {
      const res = await fetchGithubReadme(url);
      if (res.ok && res.text) {
        setPortfolioReadme({ repoLabel: githubRepoLabel(url), text: res.text });
      } else {
        setPortfolioReadme(null);
        setPortfolioReadmeError(GITHUB_README_ERROR_COPY[res.errorCode ?? ""] ?? GITHUB_README_ERROR_FALLBACK);
      }
    } catch (e) {
      setPortfolioReadme(null);
      setPortfolioReadmeError(e instanceof Error ? e.message : GITHUB_README_ERROR_FALLBACK);
    } finally {
      setPortfolioReadmeLoading(false);
    }
  }, [links.github]);

  const removePortfolioReadme = useCallback(() => {
    setPortfolioReadme(null);
    setPortfolioReadmeError(null);
  }, []);

  // ── 서류 업로드: 자소서/이력서/포폴 → /file/upload → fileId 보관 ──
  const addDoc = useCallback(
    async (slot: DocItem["slot"], kind: DocItem["kind"], file: File) => {
      const item: DocItem = { slot, kind, file, uploading: true };
      setDocs((prev) => [...prev, item]);
      try {
        const res: UploadedFile = await uploadDocument(file, kind);
        setDocs((prev) => prev.map((d) => (d === item ? { ...d, id: res.id, uploading: false } : d)));
      } catch {
        setDocs((prev) => prev.map((d) => (d === item ? { ...d, uploading: false, error: true } : d)));
      }
    },
    [],
  );

  const removeDoc = useCallback((target: DocItem) => {
    setDocs((prev) => prev.filter((d) => d !== target));
  }, []);

  const setJdUrl = useCallback((url: string) => setJd((p) => ({ ...p, url })), []);
  const setJdText = useCallback((text: string) => setJd((p) => ({ ...p, text })), []);
  // 공고 파일은 즉시 업로드하지 않는다 — 실행 시 from-job-posting 으로 케이스 생성.
  const addJdFile = useCallback((file: File) => setJd((p) => ({ ...p, file })), []);
  const removeJdFile = useCallback(() => setJd((p) => ({ ...p, file: undefined })), []);

  const go = useCallback((next: GuideStep) => setStep(next), []);

  /**
   * ★ 공고 입력 → 지원 건 1회 생성(이미 있으면 그 id 그대로). 파일 > 붙여넣기 텍스트 > URL 순.
   * runReal(가이드 자체 실행)과 인테이크/온보딩 매핑(③④ 슬롯 회신)이 공유하는 케이스 생성 단일 경로.
   * 입력이 없으면 null, 생성 실패는 throw(호출부가 문맥에 맞게 처리).
   *
   * 재제출(caseId 이미 있음) 시: 추출이 FAILED 면 새 케이스를 또 만들지 않고 B 의 재시도 엔드포인트로
   * 같은 케이스의 추출만 재큐잉한다("같은 파일 재제출 → 영원히 실패 반복" 방지). QUEUED/RUNNING(아직
   * 진행 중)·SUCCEEDED 는 건드리지 않고 그대로 caseId 를 돌려준다 — 재시도는 FAILED 에서만 유효하다.
   */
  const ensureCase = useCallback(async (): Promise<number | null> => {
    if (caseId != null) {
      try {
        const extraction = await getLatestExtractionStatus(caseId);
        if (extraction?.status === "FAILED") {
          await retryExtraction(caseId);
        }
      } catch {
        // 상태 조회·재큐잉 실패해도 케이스 자체는 유효 — 기존 caseId 로 계속 진행(폴링이 최신 상태를 다시 봄).
      }
      return caseId;
    }
    if (jd.file) {
      const sourceType = jd.file.type.startsWith("image/") ? "IMAGE" : "PDF";
      const res = await createCaseFromFile(jd.file, sourceType);
      setCaseId(res.applicationCase.id);
      return res.applicationCase.id;
    }
    if (jd.text.trim()) {
      const res = await createCaseFromText(jd.text.trim());
      setCaseId(res.applicationCase.id);
      return res.applicationCase.id;
    }
    if (jd.url.trim()) {
      // 스킴 생략("saramin.co.kr/...")은 https 로 보정 — 백엔드가 http/https 만 받는다.
      const raw = jd.url.trim();
      const normalized = /^https?:\/\//i.test(raw) ? raw : `https://${raw}`;
      const res = await createCaseFromUrl(normalized);
      setCaseId(res.applicationCase.id);
      return res.applicationCase.id;
    }
    return null;
  }, [caseId, jd]);

  /** part-done 누적 → 실제 결과 카드 값 조립(없는 지표는 null/빈배열, 지어내지 않음). */
  const finalize = useCallback((acc: Record<string, PrepStepResult>, hasCase: boolean) => {
    const fitRes = acc.FIT;
    const profRes = acc.PROFILE;
    const writeRes = acc.WRITE;

    // FIT.detail = FitAnalysisDetailResponse { fitScore, matchedSkills, missingSkills } (순위/상위% 없음)
    const fitDetail = (fitRes?.status === "DONE" ? fitRes.detail : null) as
      | { fitScore?: number | null; matchedSkills?: string | null; missingSkills?: string | null }
      | null;
    // PROFILE.detail = ProfileAiResult { strengths[], gaps[] }
    const profDetail = (profRes?.status === "DONE" ? profRes.detail : null) as
      | { strengths?: string[] | null; gaps?: string[] | null }
      | null;

    const strengths = splitSkills(fitDetail?.matchedSkills).length
      ? splitSkills(fitDetail?.matchedSkills)
      : (profDetail?.strengths ?? []).slice(0, 6);
    const gaps = splitSkills(fitDetail?.missingSkills).length
      ? splitSkills(fitDetail?.missingSkills)
      : (profDetail?.gaps ?? []).slice(0, 6);

    const ranParts = Object.values(acc).filter((r) => r.status === "DONE").map((r) => r.key);
    // 케이스는 만들었는데 FIT 이 DONE 이 아니면(스킵/실패/대기) → 비동기 추출 대기로 간주.
    const fitPending = hasCase && fitRes?.status !== "DONE";

    setFit({
      fitScore: fitDetail?.fitScore ?? null,
      strengths,
      gaps,
      written: writeRes?.status === "DONE",
      ranParts,
      fitPending,
    });
    setStep("fit");
  }, []);

  /**
   * ★ 실제 실행: collect() 스냅샷 → 공고 케이스 생성 → AutoPrepRequest 조립 → 오케 SSE.
   *   목값 없음. 결과 카드는 실제 part-done 으로만 채운다.
   */
  const runReal = useCallback(async () => {
    setStep("analyzing");
    setRunError(null);
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    // 1) 공고 → 지원 건(case). 생성 로직은 ensureCase 공유(인테이크 매핑과 단일 경로).
    let effectiveCaseId = caseId;
    try {
      effectiveCaseId = await ensureCase();
    } catch (e) {
      // 케이스 생성 실패해도 자소서(WRITE)만으로 진행 — 오케는 caseId 없이도 돈다.
      setRunError(e instanceof Error ? e.message : "공고 지원 건 생성에 실패했어요.");
    }

    // 2) AutoPrepRequest 조립.
    //    - 자소서(ATTACHMENT) 만 attachmentFileIds 로 (이력서/포폴 제외 — 소비 핸들러 없음).
    const attachmentFileIds = docs
      .filter((d) => d.kind === "ATTACHMENT" && d.id != null)
      .map((d) => d.id as number);
    const req: AutoPrepRequest = {
      applicationCaseId: effectiveCaseId,
      attachmentFileIds: attachmentFileIds.length ? attachmentFileIds : undefined,
      // coverLetterText 는 자소서를 attachment 로 실으므로 비움. mode/query 는 가이드에서 미지정.
    };

    // 3) 오케 SSE 실행 — 실제 part-done 누적.
    const acc: Record<string, PrepStepResult> = {};
    try {
      await runStream(
        req,
        (evt) => {
          if (evt.type === "part-done") acc[evt.result.key] = evt.result;
          else if (evt.type === "error") setRunError(evt.message);
        },
        controller.signal,
      );
    } catch (e) {
      if (controller.signal.aborted) return;
      setRunError(e instanceof Error ? e.message : "준비 실행에 실패했어요.");
    }
    if (controller.signal.aborted) return;
    finalize(acc, effectiveCaseId != null);
  }, [caseId, ensureCase, docs, finalize]);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    setStep("role");
    setRoleState(null);
    setCustomRole("");
    setSkills([]);
    setDocs([]);
    setJd({ url: "", text: "" });
    setLinks({});
    setPortfolioReadme(null);
    setPortfolioReadmeLoading(false);
    setPortfolioReadmeError(null);
    setCaseId(null);
    setFit(null);
    setRunError(null);
  }, []);

  /**
   * ★ 수집 스냅샷 — 받은 fileId·링크·케이스를 한 곳에.
   * 이력서/포폴은 여기 fileId 로만 남긴다(오케 미전송). 프로필 반영은 향후 별도 작업.
   */
  const collect = useCallback(() => {
    return {
      role: role === "__custom__" ? customRole.trim() : role,
      skills,
      coverLetterFileIds: docs.filter((d) => d.kind === "ATTACHMENT" && d.id != null).map((d) => d.id as number),
      // TODO(A파트 배선): resume/portfolio fileId 를 프로필/케이스에 참조로 연결(적합도엔 미투입).
      resumeFileIds: docs.filter((d) => d.kind === "RESUME" && d.id != null).map((d) => d.id as number),
      portfolioFileIds: docs.filter((d) => d.kind === "PORTFOLIO" && d.id != null).map((d) => d.id as number),
      // TODO(A파트 배선): 프로필/분석 반영은 향후 배선.
      portfolioReadmeText: portfolioReadme?.text ?? null,
      applicationCaseId: caseId,
      jdHasFile: !!jd.file,
      jdUrl: jd.url.trim() || null,
      jdText: jd.text.trim() || null,
      links,
    };
  }, [role, customRole, skills, docs, caseId, jd, links, portfolioReadme]);

  return {
    step,
    role,
    customRole,
    setCustomRole,
    field,
    skills,
    docs,
    jd,
    links,
    portfolioReadme,
    portfolioReadmeLoading,
    portfolioReadmeError,
    caseId,
    fit,
    runError,
    // actions
    setRole,
    toggleSkill,
    setLink,
    fetchPortfolioReadme,
    removePortfolioReadme,
    addDoc,
    removeDoc,
    setJdUrl,
    setJdText,
    addJdFile,
    removeJdFile,
    go,
    ensureCase,
    runReal,
    reset,
    collect,
  };
}
