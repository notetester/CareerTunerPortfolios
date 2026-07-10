import { useCallback, useEffect, useRef, useState } from "react";

import { useAutoPrepRun, type PartState } from "@/features/autoprep/hooks/useAutoPrepRun";
import type { AutoPrepRequest, PrepStepResult } from "@/features/autoprep/types/autoPrep";
import {
  getProfile,
  importProfileDocument,
  pollProfileAnalyze,
  saveProfile,
  startProfileAnalyze,
  type ProfileAnalyzeDraft,
} from "@/app/profile/profileApi";
import { mergeApprovedProfileDraft, type DraftApplyOpts } from "@/app/profile/profileDraftMerge";
import {
  createCaseFromFile, createCaseFromText, createCaseFromUrl, fetchGithubReadme, getLatestExtractionStatus,
  uploadDocument, type UploadedFile,
} from "../api/onboardingApi";
import {
  getField, githubRepoLabel, GITHUB_README_ERROR_COPY, GITHUB_README_ERROR_FALLBACK, ROLES,
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
/**
 * 재제출 시 공고 추출이 FAILED 인 경우 — 온보딩에서 자동 재추출(무인자)은 strict 정책상 금지다.
 * 사용자가 지원 건 상세에서 OCR 모델을 골라 재추출해야 하므로, 분석 진행을 막고 caseId 를 실어 안내한다.
 */
export class OnboardingPostingExtractionFailedError extends Error {
  constructor(public readonly caseId: number) {
    super("공고문 추출에 실패했어요. 지원 건 상세에서 OCR 모델을 골라 다시 추출한 뒤 이어가 주세요.");
    this.name = "OnboardingPostingExtractionFailedError";
  }
}

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
  // 재제출 시 공고 추출이 FAILED 라 분석을 차단한 지원 건 id — UI 가 '다시 분석하기'(반복 차단) 대신
  // '지원 건 상세로 이동'만 노출하도록 신호한다. 성공 경로/새 시도마다 ensureCase 가 초기화한다.
  const [extractionFailedCaseId, setExtractionFailedCaseId] = useState<number | null>(null);
  /** 이력서 구조화 초안 — React state 보관(D-1). 새로고침 시 소실. 자동 DB 커밋 금지. */
  const [profileDraft, setProfileDraft] = useState<ProfileAnalyzeDraft | null>(null);
  const [profileDraftStatus, setProfileDraftStatus] = useState<"idle" | "running" | "done" | "failed">("idle");
  const [profileDraftError, setProfileDraftError] = useState<string | null>(null);
  const [profileImportNotice, setProfileImportNotice] = useState<string | null>(null);

  // 실제 오케 SSE 실행 — useChatbot 이 쓰는 것과 동일한 공용 훅(파싱/누적 로직 단일 소스, 여긴 재사용만).
  const run = useAutoPrepRun();
  const hasCaseRef = useRef(false);
  const field = getField(role);

  const setRole = useCallback((r: string) => {
    setRoleState(r);
    setSkills([]);
  }, []);

  /**
   * ④ 재진입 하이드레이션 — 서버가 확정 보관 중인 수집값(직무·기술 원문)을 빈 필드에만 1회 주입한다.
   * 재마운트 직후 호출돼 명세 보드가 "전부 초기화"로 보이는 것을 막는다(표시용 — 진행 판정은 서버 step).
   * 직무가 직군 칩(ROLES)과 정확일치하면 칩 선택으로, 아니면 직접 입력으로 복원. 기술은 가이드 제출
   * 포맷(콤마 join)을 역파싱. 세션 내 사용자 입력이 이미 있으면 절대 덮지 않는다(functional update 가드).
   */
  const hydrate = useCallback((collected: { job?: string | null; skills?: string | null }) => {
    const job = collected.job?.trim();
    if (job) {
      if (ROLES.includes(job)) {
        setRoleState((prev) => prev ?? job);
      } else {
        setRoleState((prev) => prev ?? "__custom__");
        setCustomRole((prev) => (prev ? prev : job));
      }
    }
    const skillsText = collected.skills?.trim();
    if (skillsText) {
      const parsed = skillsText.split(",").map((s) => s.trim()).filter(Boolean);
      if (parsed.length > 0) {
        setSkills((prev) => (prev.length > 0 ? prev : parsed));
      }
    }
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
  // 이력서: import(RESUME_TEXT) + analyze(구조화 초안). 자소서: import(SELF_INTRO).
  // 구조화 필드는 자동 커밋하지 않음 — profileDraft 확인 후 applyProfileDraft.
  const addDoc = useCallback(
    async (slot: DocItem["slot"], kind: DocItem["kind"], file: File) => {
      const item: DocItem = { slot, kind, file, uploading: true };
      setDocs((prev) => [...prev, item]);
      setProfileImportNotice(null);
      try {
        const res: UploadedFile = await uploadDocument(file, kind);
        setDocs((prev) => prev.map((d) => (d === item ? { ...d, id: res.id, uploading: false } : d)));

        if (slot === "resume" && res.id != null) {
          try {
            const imported = await importProfileDocument(res.id, "RESUME_TEXT");
            setProfileImportNotice(
              imported.truncated
                ? "이력서 일부만 프로필에 저장했어요."
                : "이력서 원문을 프로필에 넣었어요. 구조화 분석 중…",
            );
            setProfileDraftStatus("running");
            setProfileDraft(null);
            setProfileDraftError(null);
            const started = await startProfileAnalyze(res.id);
            const finished = await pollProfileAnalyze(started.jobId);
            if (finished.status === "DONE" && finished.draft) {
              setProfileDraft(finished.draft);
              setProfileDraftStatus("done");
              setProfileImportNotice("이력서에서 학력·경력 등을 뽑았어요. 아래 확인 후 반영해 주세요.");
            } else {
              setProfileDraftStatus("failed");
              setProfileDraftError(
                finished.errorMessage || "구조화 분석은 실패했어요. 폼을 직접 채워주세요.",
              );
              setProfileImportNotice(null);
            }
          } catch (e) {
            setProfileDraftStatus("failed");
            setProfileDraftError(e instanceof Error ? e.message : "첨부에 실패했어요. 다시 시도해 주세요.");
            setProfileImportNotice(null);
          }
        } else if (slot === "cover" && res.id != null) {
          try {
            const imported = await importProfileDocument(res.id, "SELF_INTRO");
            setProfileImportNotice(
              imported.truncated
                ? "자소서 일부만 프로필에 저장했어요."
                : "자기소개서 원문을 프로필에 넣었어요.",
            );
          } catch (e) {
            setProfileImportNotice(
              e instanceof Error ? e.message : "자소서 프로필 반영에 실패했어요.",
            );
          }
        }
      } catch {
        setDocs((prev) => prev.map((d) => (d === item ? { ...d, uploading: false, error: true } : d)));
      }
    },
    [],
  );

  /**
   * 구조화 초안 중 승인 필드만 PUT /profile. 자동 커밋 경로가 아님 — 사용자 확인 후 호출.
   * saveOnboardingProfile(:720) 이후 시점에 호출되도록 가이드 docs→jd 흐름에서 확인 카드를 둔다.
   */
  const applyProfileDraft = useCallback(
    async (opts: DraftApplyOpts) => {
      if (!profileDraft) return;
      // 현재 프로필 RMW + 승인·비어 있지 않은 배열만 교체(mergeApprovedProfileDraft).
      const cur = await getProfile();
      const body = mergeApprovedProfileDraft(cur, profileDraft, opts);
      await saveProfile(body);
      setProfileDraft(null);
      setProfileDraftStatus("idle");
      setProfileImportNotice("선택한 항목을 프로필에 반영했어요.");
    },
    [profileDraft],
  );

  const dismissProfileDraft = useCallback(() => {
    setProfileDraft(null);
    setProfileDraftStatus("idle");
    setProfileDraftError(null);
  }, []);

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
    setExtractionFailedCaseId(null); // 매 시도마다 초기화 — 이전 실패 신호가 남지 않게.
    if (caseId != null) {
      // 재제출: 추출이 FAILED 면 자동 재추출하지 않는다(strict 정책 — 사용자 OCR 모델 선택 필수).
      // 온보딩에서 무인자 재추출을 호출하면 백엔드가 400 을 주므로, 분석을 진행하지 않고 사용자를
      // 지원 건 상세로 보내 OCR 모델을 골라 재추출하도록 막고 안내한다.
      let failed = false;
      try {
        const extraction = await getLatestExtractionStatus(caseId);
        failed = extraction?.status === "FAILED";
      } catch {
        // 상태 조회 실패는 무시 — 케이스 자체는 유효(폴링이 최신 상태를 다시 봄).
      }
      if (failed) {
        setExtractionFailedCaseId(caseId);
        throw new OnboardingPostingExtractionFailedError(caseId);
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

  /** part-done 누적(useAutoPrepRun 의 PartState[]) → 실제 결과 카드 값 조립(없는 지표는 null/빈배열, 지어내지 않음). */
  const finalize = useCallback((parts: PartState[], hasCase: boolean) => {
    const acc: Record<string, PrepStepResult> = {};
    parts.forEach((p) => { if (p.result) acc[p.key] = p.result; });
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
   *   목값 없음. SSE 구독/누적은 useAutoPrepRun(run)이 전담 — 완료 감지는 아래 useEffect.
   *
   * extraAttachmentIds: 방금 업로드해 아직 docs 상태에 반영되지 않은 첨부(attachCoverLetter 경로).
   *   setDocs 는 비동기라 이 클로저의 docs 가 못 보므로 id 를 직접 받아 합친다.
   */
  const runReal = useCallback(async (extraAttachmentIds: number[] = []) => {
    // 재실행(뒤로→jd→다시 다음) 대비 클린 슬레이트 — run.start 전 잠깐의 await 구간에서
    // 아래 완료 감지 effect 가 "이전 실행의 settled parts"를 이번 실행의 완료로 오인하지 않게 한다.
    run.reset();
    setStep("analyzing");
    setRunError(null);

    // 1) 공고 → 지원 건(case). 생성 로직은 ensureCase 공유(인테이크 매핑과 단일 경로).
    let effectiveCaseId = caseId;
    try {
      effectiveCaseId = await ensureCase();
    } catch (e) {
      if (e instanceof OnboardingPostingExtractionFailedError) {
        // 공고 추출 실패 → 분석을 진행하지 않고 사용자를 상세로 유도(무인자 자동 재추출 금지).
        setRunError(e.message);
        return;
      }
      // 케이스 생성 실패해도 자소서(WRITE)만으로 진행 — 오케는 caseId 없이도 돈다.
      setRunError(e instanceof Error ? e.message : "공고 지원 건 생성에 실패했어요.");
    }
    hasCaseRef.current = effectiveCaseId != null;

    // 2) AutoPrepRequest 조립.
    //    - 자소서(ATTACHMENT) 만 attachmentFileIds 로 (이력서/포폴 제외 — 소비 핸들러 없음).
    const attachmentFileIds = Array.from(new Set([
      ...docs.filter((d) => d.kind === "ATTACHMENT" && d.id != null).map((d) => d.id as number),
      ...extraAttachmentIds,
    ]));
    const req: AutoPrepRequest = {
      applicationCaseId: effectiveCaseId,
      attachmentFileIds: attachmentFileIds.length ? attachmentFileIds : undefined,
      // coverLetterText 는 자소서를 attachment 로 실으므로 비움. mode/query 는 가이드에서 미지정.
    };

    // 3) 오케 SSE 실행 — run.parts 누적은 useAutoPrepRun 내부, 완료는 아래 effect 가 감지해 finalize.
    void run.start(req);
  }, [caseId, ensureCase, docs, run]);

  /**
   * SKIPPED 된 WRITE 카드에서 자소서를 뒤늦게 첨부 → 그 자리에서 재실행.
   * docs 스텝을 건너뛴 사용자가 앞 단계로 되돌아가지 않고도 교정을 이어받게 한다.
   */
  const attachCoverLetter = useCallback(async (file: File) => {
    const uploaded: UploadedFile = await uploadDocument(file, "ATTACHMENT");
    setDocs((prev) => [...prev, { slot: "cover", kind: "ATTACHMENT", file, id: uploaded.id, uploading: false }]);
    await runReal([uploaded.id]);
  }, [runReal]);

  // ★ 실행 완료 감지: analyzing 단계에서 run 이 멈추고(running=false) 전 파트가 settle 되면 결과 조립.
  //   run.parts 는 useAutoPrepRun 이 SSE 로 채우는 살아있는 상태라 클로저 문제 없이 항상 최신값을 본다.
  //   "시작함" 판정은 parts 또는 error 둘 중 하나라도 채워졌는지로 — parts 만 보면 plan 도 못 받고
  //   바로 실패한 경우(네트워크 즉시 오류) 영원히 analyzing 에 멈춘다.
  useEffect(() => {
    if (step !== "analyzing") return;
    if (run.running) return;
    const started = run.parts.length > 0 || run.error != null;
    if (!started) return; // runReal 의 run.reset() 직후 ~ run.start 호출 전(await ensureCase 구간) — 아직 시작 전
    const allSettled = run.parts.every((p) => p.status !== "pending" && p.status !== "running");
    if (!allSettled) return;
    finalize(run.parts, hasCaseRef.current);
  }, [step, run.running, run.parts, run.error, finalize]);

  const reset = useCallback(() => {
    run.reset();
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
  }, [run]);

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
    extractionFailedCaseId,
    fit,
    runError: runError ?? run.error,
    runParts: run.parts,
    runRunning: run.running,
    profileDraft,
    profileDraftStatus,
    profileDraftError,
    profileImportNotice,
    // actions
    setRole,
    hydrate,
    toggleSkill,
    setLink,
    fetchPortfolioReadme,
    removePortfolioReadme,
    addDoc,
    removeDoc,
    applyProfileDraft,
    dismissProfileDraft,
    setJdUrl,
    setJdText,
    addJdFile,
    removeJdFile,
    go,
    ensureCase,
    runReal,
    attachCoverLetter,
    reset,
    collect,
  };
}
