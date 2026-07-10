import { useCallback, useEffect, useRef, useState } from "react";

import { useAutoPrepRun, type PartState } from "@/features/autoprep/hooks/useAutoPrepRun";
import type { AutoPrepRequest, PrepStepResult } from "@/features/autoprep/types/autoPrep";
import {
  deleteProfilePortfolioFile,
  deleteUnlinkedProfileFile,
  getProfile,
  importProfileDocument,
  linkProfilePortfolioFiles,
  pollProfileAnalyze,
  saveProfile,
  startProfileAnalyze,
  uploadProfilePortfolioFile,
  type ProfileAnalyzeDraft,
} from "@/app/profile/profileApi";
import { mergeApprovedProfileDraft, type DraftApplyOpts } from "@/app/profile/profileDraftMerge";
import {
  createCaseFromFile, createCaseFromText, createCaseFromUrl, fetchGithubReadme, getLatestExtractionStatus,
  retryExtraction, uploadDocument, type UploadedFile,
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
  clientId: number;
  slot: "cover" | "resume" | "portfolio";
  kind: "RESUME" | "PORTFOLIO" | "ATTACHMENT";
  file: File;
  id?: number;
  uploading: boolean;
  deleting?: boolean;
  error?: boolean;
  deleteError?: string;
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

function profileLinkList(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(String).map((item) => item.trim()).filter(Boolean);
  if (typeof value !== "string" || !value.trim()) return [];
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) return parsed.map(String).map((item) => item.trim()).filter(Boolean);
  } catch {
    // 구형 일반 문자열은 줄 단위로 복구한다.
  }
  return value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
}

/**
 * 온보딩 가이드 상태 + 실제 오케스트레이터 배선.
 * - 자소서(ATTACHMENT) → attachmentFileIds 로 오케에 실음(WRITE 가 소비).
 * - 이력서(RESUME) → 프로필 원문/구조화 초안으로 연결.
 * - 포폴(PORTFOLIO) → 업로드 시 USER_PROFILE_PORTFOLIO ref 로 영속 연결하고 AutoPrep PROFILE 근거로 사용.
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
  /** 이력서 구조화 초안 — React state 보관(D-1). 새로고침 시 소실. 자동 DB 커밋 금지. */
  const [profileDraft, setProfileDraft] = useState<ProfileAnalyzeDraft | null>(null);
  const [profileDraftStatus, setProfileDraftStatus] = useState<"idle" | "running" | "done" | "failed">("idle");
  const [profileDraftError, setProfileDraftError] = useState<string | null>(null);
  const [profileImportNotice, setProfileImportNotice] = useState<string | null>(null);

  // 실제 오케 SSE 실행 — useChatbot 이 쓰는 것과 동일한 공용 훅(파싱/누적 로직 단일 소스, 여긴 재사용만).
  const run = useAutoPrepRun();
  const hasCaseRef = useRef(false);
  const nextDocClientIdRef = useRef(0);
  const docGenerationRef = useRef(0);
  const activeDocIdsRef = useRef(new Set<number>());
  const removingDocIdsRef = useRef(new Set<number>());
  const handedOffFileIdsRef = useRef(new Set<number>());
  const pendingResumeAnalysisIdsRef = useRef(new Set<number>());
  const field = getField(role);

  const isDocCurrent = useCallback((clientId: number, generation: number) =>
    generation === docGenerationRef.current
      && activeDocIdsRef.current.has(clientId)
      && !removingDocIdsRef.current.has(clientId), []);

  const deleteDocFile = useCallback((doc: Pick<DocItem, "kind" | "id">): Promise<void> => {
    if (doc.id == null) return Promise.resolve();
    return doc.kind === "PORTFOLIO"
      ? deleteProfilePortfolioFile(doc.id)
      : deleteUnlinkedProfileFile(doc.id);
  }, []);

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

  // ── 서류 업로드: 자소서/이력서는 /file/upload, 포폴은 프로필 연결 전용 API → fileId 보관 ──
  // 이력서: import(RESUME_TEXT) + analyze(구조화 초안). 자소서: import(SELF_INTRO).
  // 구조화 필드는 자동 커밋하지 않음 — profileDraft 확인 후 applyProfileDraft.
  const addDoc = useCallback(
    async (slot: DocItem["slot"], kind: DocItem["kind"], file: File) => {
      const generation = docGenerationRef.current;
      const clientId = ++nextDocClientIdRef.current;
      const item: DocItem = { clientId, slot, kind, file, uploading: true };
      activeDocIdsRef.current.add(clientId);
      setDocs((prev) => [...prev, item]);
      setProfileImportNotice(null);
      try {
        const res: UploadedFile = slot === "portfolio"
          ? await uploadProfilePortfolioFile(file)
          : await uploadDocument(file, kind);
        if (!isDocCurrent(clientId, generation)) {
          // 닫기/reset 중 완료된 업로드는 UI에 되살리지 않고 즉시 정리한다.
          await deleteDocFile({ kind, id: res.id }).catch(() => undefined);
          return;
        }
        setDocs((prev) => prev.map((d) => (d === item ? { ...d, id: res.id, uploading: false } : d)));

        if (slot === "resume" && res.id != null) {
          try {
            const imported = await importProfileDocument(res.id, "RESUME_TEXT");
            if (!isDocCurrent(clientId, generation)) {
              await deleteUnlinkedProfileFile(res.id).catch(() => undefined);
              return;
            }
            setProfileImportNotice(
              imported.truncated
                ? "이력서 일부만 프로필에 저장했어요."
                : "이력서 원문을 프로필에 넣었어요. 구조화 분석 중…",
            );
            setProfileDraftStatus("running");
            setProfileDraft(null);
            setProfileDraftError(null);
            pendingResumeAnalysisIdsRef.current.add(res.id);
            try {
              const started = await startProfileAnalyze(res.id);
              const finished = await pollProfileAnalyze(started.jobId);
              if (isDocCurrent(clientId, generation)) {
                if (finished.status === "DONE" && finished.draft) {
                  setProfileDraft(finished.draft);
                  setProfileDraftStatus("done");
                  setProfileImportNotice("이력서에서 학력·경력 등을 뽑았어요. 원본 파일은 정리했으며 아래 확인 후 반영해 주세요.");
                } else {
                  setProfileDraftStatus("failed");
                  setProfileDraftError(
                    finished.errorMessage || "구조화 분석은 실패했어요. 폼을 직접 채워주세요.",
                  );
                  setProfileImportNotice(null);
                }
              }
            } catch (analysisError) {
              if (isDocCurrent(clientId, generation)) {
                setProfileDraftStatus("failed");
                setProfileDraftError(
                  analysisError instanceof Error
                    ? analysisError.message
                    : "구조화 분석은 실패했어요. 폼을 직접 채워주세요.",
                );
                setProfileImportNotice(null);
              }
            } finally {
              pendingResumeAnalysisIdsRef.current.delete(res.id);
              try {
                await deleteUnlinkedProfileFile(res.id);
                activeDocIdsRef.current.delete(clientId);
                setDocs((prev) => prev.filter((d) => d.clientId !== clientId));
              } catch (cleanupError) {
                setDocs((prev) => prev.map((d) => d.clientId === clientId
                  ? {
                      ...d,
                      deleting: false,
                      deleteError: cleanupError instanceof Error
                        ? cleanupError.message
                        : "분석을 마친 이력서 원본을 정리하지 못했어요.",
                    }
                  : d));
              } finally {
                removingDocIdsRef.current.delete(clientId);
              }
            }
          } catch (e) {
            if (!isDocCurrent(clientId, generation)) return;
            setProfileDraftStatus("failed");
            setProfileDraftError(e instanceof Error ? e.message : "첨부에 실패했어요. 다시 시도해 주세요.");
            setProfileImportNotice(null);
          }
        } else if (slot === "cover" && res.id != null) {
          try {
            const imported = await importProfileDocument(res.id, "SELF_INTRO");
            if (!isDocCurrent(clientId, generation)) return;
            setProfileImportNotice(
              imported.truncated
                ? "자소서 일부만 프로필에 저장했어요."
                : "자기소개서 원문을 프로필에 넣었어요.",
            );
          } catch (e) {
            if (!isDocCurrent(clientId, generation)) return;
            setProfileImportNotice(
              e instanceof Error ? e.message : "자소서 프로필 반영에 실패했어요.",
            );
          }
        }
      } catch {
        if (isDocCurrent(clientId, generation)) {
          setDocs((prev) => prev.map((d) => (d.clientId === clientId ? { ...d, uploading: false, error: true } : d)));
        }
      }
    },
    [deleteDocFile, isDocCurrent],
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

  const removeDoc = useCallback(async (target: DocItem) => {
    if (target.uploading || target.deleting) return;
    if (target.id == null) {
      activeDocIdsRef.current.delete(target.clientId);
      setDocs((prev) => prev.filter((d) => d.clientId !== target.clientId));
      return;
    }
    if (target.kind === "ATTACHMENT" && handedOffFileIdsRef.current.has(target.id)) {
      setDocs((prev) => prev.map((d) => d.clientId === target.clientId
        ? { ...d, deleteError: "이미 자동 준비 실행에 인계된 자소서 파일은 여기서 삭제할 수 없어요." }
        : d));
      return;
    }
    const consequence = target.kind === "PORTFOLIO"
      ? "이 포트폴리오 파일을 삭제할까요? 다음 AI 분석의 근거에서도 제거됩니다."
      : "업로드 파일을 삭제할까요? 이미 프로필로 가져온 내용은 유지되며 내 프로필에서 따로 수정하거나 삭제할 수 있어요.";
    if (!window.confirm(consequence)) return;

    if (target.kind === "RESUME" && pendingResumeAnalysisIdsRef.current.has(target.id)) {
      // 분석 worker가 원본을 읽는 동안은 물리 삭제를 미루고, 완료 finally에서 정리한다.
      removingDocIdsRef.current.add(target.clientId);
      setDocs((prev) => prev.map((d) => d.clientId === target.clientId
        ? {
            ...d,
            deleting: true,
            deleteError: "이력서 분석이 끝나면 원본 파일을 자동으로 삭제할게요.",
          }
        : d));
      return;
    }

    removingDocIdsRef.current.add(target.clientId);
    setDocs((prev) => prev.map((d) => d.clientId === target.clientId
      ? { ...d, deleting: true, deleteError: undefined }
      : d));
    try {
      await deleteDocFile(target);
      activeDocIdsRef.current.delete(target.clientId);
      setDocs((prev) => prev.filter((d) => d.clientId !== target.clientId));
    } catch (error) {
      setDocs((prev) => prev.map((d) => d.clientId === target.clientId
        ? {
            ...d,
            deleting: false,
            deleteError: error instanceof Error ? error.message : "업로드 파일을 삭제하지 못했어요.",
          }
        : d));
    } finally {
      removingDocIdsRef.current.delete(target.clientId);
    }
  }, [deleteDocFile]);

  /** collect()가 가진 포트폴리오 id를 서버 프로필 참조와 재동기화(중단 재시도·구형 업로드 입양). */
  const syncPortfolioFiles = useCallback(async (fileIds?: number[]) => {
    const ids = fileIds ?? docs
      .filter((d) => d.kind === "PORTFOLIO" && d.id != null)
      .map((d) => d.id as number);
    if (ids.length > 0) {
      await linkProfilePortfolioFiles(Array.from(new Set(ids)));
    }
  }, [docs]);

  /** 가이드에서 받은 URL을 기존 프로필 링크와 합집합으로 저장한다(RMW, 다른 프로필 필드 보존). */
  const syncPortfolioLinks = useCallback(async (collectedLinks?: Partial<Record<LinkKey, string>>) => {
    const values = Object.values(collectedLinks ?? links)
      .map((value) => value?.trim() ?? "")
      .filter(Boolean);
    if (values.length === 0) return;
    const current = await getProfile();
    const existing = profileLinkList(current.portfolioLinks);
    const seen = new Set(existing.map((value) => value.toLowerCase()));
    const merged = [...existing];
    for (const value of values) {
      const key = value.toLowerCase();
      if (!seen.has(key)) {
        seen.add(key);
        merged.push(value);
      }
    }
    await saveProfile({ ...current, portfolioLinks: merged });
  }, [links]);

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

    // collect 대상 PORTFOLIO 파일은 자소서 attachment와 분리해 프로필 ref만 재확인한다.
    try {
      await Promise.all([syncPortfolioFiles(), syncPortfolioLinks()]);
    } catch (e) {
      // 전용 업로드가 이미 profile ref 를 기록한다. 재동기화 실패만으로 전체 준비를 막지는 않고 안내한다.
      setProfileImportNotice(e instanceof Error ? e.message : "포트폴리오 연결 상태를 다시 확인해 주세요.");
    }

    // 1) 공고 → 지원 건(case). 생성 로직은 ensureCase 공유(인테이크 매핑과 단일 경로).
    let effectiveCaseId = caseId;
    try {
      effectiveCaseId = await ensureCase();
    } catch (e) {
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

    attachmentFileIds.forEach((id) => handedOffFileIdsRef.current.add(id));

    // 3) 오케 SSE 실행 — run.parts 누적은 useAutoPrepRun 내부, 완료는 아래 effect 가 감지해 finalize.
    void run.start(req);
  }, [caseId, ensureCase, docs, run, syncPortfolioFiles, syncPortfolioLinks]);

  /**
   * SKIPPED 된 WRITE 카드에서 자소서를 뒤늦게 첨부 → 그 자리에서 재실행.
   * docs 스텝을 건너뛴 사용자가 앞 단계로 되돌아가지 않고도 교정을 이어받게 한다.
   */
  const attachCoverLetter = useCallback(async (file: File) => {
    const generation = docGenerationRef.current;
    const clientId = ++nextDocClientIdRef.current;
    activeDocIdsRef.current.add(clientId);
    const uploaded: UploadedFile = await uploadDocument(file, "ATTACHMENT");
    if (!isDocCurrent(clientId, generation)) {
      await deleteUnlinkedProfileFile(uploaded.id).catch(() => undefined);
      return;
    }
    setDocs((prev) => [...prev, {
      clientId,
      slot: "cover",
      kind: "ATTACHMENT",
      file,
      id: uploaded.id,
      uploading: false,
    }]);
    await runReal([uploaded.id]);
  }, [isDocCurrent, runReal]);

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

  const markDocsHandedOff = useCallback((fileIds?: number[]) => {
    const ids = fileIds ?? docs.flatMap((doc) =>
      doc.kind === "ATTACHMENT" && doc.id != null ? [doc.id] : []);
    ids.forEach((id) => handedOffFileIdsRef.current.add(id));
  }, [docs]);

  const reset = useCallback(() => {
    const abandoned = docs.filter((doc) =>
      doc.id != null
      && doc.kind !== "PORTFOLIO"
      && !handedOffFileIdsRef.current.has(doc.id)
      && !(doc.kind === "RESUME" && pendingResumeAnalysisIdsRef.current.has(doc.id)));
    docGenerationRef.current += 1;
    activeDocIdsRef.current.clear();
    removingDocIdsRef.current.clear();
    // 닫기는 즉시 진행하되, 이미 서버에 생긴 미인계 파일은 소유권 검증 DELETE로 best-effort 정리한다.
    void Promise.allSettled(abandoned.map((doc) => deleteDocFile(doc)));
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
    setProfileDraft(null);
    setProfileDraftStatus("idle");
    setProfileDraftError(null);
    setProfileImportNotice(null);
  }, [deleteDocFile, docs, run]);

  /**
   * ★ 수집 스냅샷 — 받은 fileId·링크·케이스를 한 곳에.
   * 포폴 fileId 는 syncPortfolioFiles 가 USER_PROFILE_PORTFOLIO 참조로 영속 연결한다.
   */
  const collect = useCallback(() => {
    return {
      role: role === "__custom__" ? customRole.trim() : role,
      skills,
      coverLetterFileIds: docs.filter((d) => d.kind === "ATTACHMENT" && d.id != null).map((d) => d.id as number),
      resumeFileIds: docs.filter((d) => d.kind === "RESUME" && d.id != null).map((d) => d.id as number),
      portfolioFileIds: docs.filter((d) => d.kind === "PORTFOLIO" && d.id != null).map((d) => d.id as number),
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
    syncPortfolioFiles,
    syncPortfolioLinks,
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
    markDocsHandedOff,
    reset,
    collect,
  };
}
