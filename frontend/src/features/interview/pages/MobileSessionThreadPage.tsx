import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { ArrowLeft, ArrowUp, Camera, Check, Mic, Monitor, Sparkles, Trash2, X } from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { isOutageFallbackActive } from "@/app/lib/outageFallback";
import { onAppLockState } from "@/platform/appLockEvents";
import { haptic } from "@/platform/haptics";
import { useApplicationCases } from "@/features/applications/hooks/useApplicationCases";
import { AiChargeCostBadge } from "@/features/billing/components/AiChargeCostBadge";
import {
  dispatchSessionToDesktop,
  deleteAnswerMedia,
  deletePendingInterviewFile,
  generateExpectedQuestions,
  generateFollowUps,
  getModelAnswer,
  getSessionReview,
  listInterviewSessions,
  listSessionQuestions,
  markSessionResumed,
  submitAnswer,
  uploadFile,
} from "../api/interviewApi";
import { getInterviewModeLabel } from "../types/interview";
import type { FileAsset, InterviewQuestion, InterviewSession, SessionReviewItem } from "../types/interview";
import {
  buildPendingMediaAnswerFields,
  capturedDraft,
  classifySubmissionFailure,
  cleanupEligiblePendingFileIds,
  concludeMissingSubmissionReconciliation,
  findReconciledAnswer,
  restoreFailedDraft,
  rollbackOptimisticSubmission,
  settleMediaUploadGeneration,
  SUBMISSION_RECONCILE_DELAYS_MS,
  validateCapturedMediaSize,
  type PendingInterviewMediaKind,
} from "../lib/mobileSubmission";
import { ImmersiveVoiceOverlay } from "../components/mobile/ImmersiveVoiceOverlay";
import { ImmersiveAvatarOverlay } from "../components/mobile/ImmersiveAvatarOverlay";
import {
  PermissionPreprompt,
  isPrepromptAccepted,
  type PermKind,
} from "../components/mobile/PermissionPreprompt";

/**
 * 모바일 세션 스레드 (Claude 앱 문법) — 질문→답변→채점 카드→꼬리질문 대화 타임라인.
 * 데스크탑 v2 SessionThread 와 동형. 딥링크(/m/session/:id)로 디스패치 알림에서 직행한다.
 * 하단 입력 독: 텍스트 답변 + 몰입형 음성/화상 답변 진입.
 */

interface ScoreInfo {
  qid: number;
  score: number | null;
  feedback: string | null;
  improvedAnswer: string | null;
  modelAnswer: string | null;
  voiceScore: number | null;
  visualScore: number | null;
}
type ThreadItem = (
  | { kind: "question"; q: InterviewQuestion }
  | {
      kind: "answer";
      qid: number;
      answerId: number | null;
      text: string;
      hasAudio: boolean;
      hasVideo: boolean;
      audioUrl: string | null;
      videoUrl: string | null;
    }
  | { kind: "score"; s: ScoreInfo }
  | { kind: "scoring" }
) & { submissionId?: string };

interface PendingInterviewMedia {
  questionId: number;
  kind: PendingInterviewMediaKind;
  file: FileAsset;
  voiceScore: number | null;
  visualScore: number | null;
}

interface PendingAnswerSubmission {
  sessionId: number;
  sessionGeneration: number;
  questionId: number;
  text: string;
  submissionId: string;
  media: PendingInterviewMedia | null;
  voiceScore: number | null;
  visualScore: number | null;
}

type SubmissionReconciliation =
  | { status: "SAVED"; item: SessionReviewItem }
  | { status: "NOT_SAVED" }
  | { status: "UNKNOWN" };

export function MobileSessionThreadPage() {
  const { id } = useParams();
  const sessionId = Number(id);
  const navigate = useNavigate();
  const { user, isAuthenticated, loading: authLoading } = useAuth();
  const cases = useApplicationCases(isAuthenticated);
  const consentScope = user ? `user:${user.id}` : "";

  const [session, setSession] = useState<InterviewSession | null>(null);
  const [items, setItems] = useState<ThreadItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [draft, setDraft] = useState("");
  const draftRef = useRef("");
  const [scoring, setScoring] = useState(false);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const [openModel, setOpenModel] = useState<Record<number, boolean>>({});
  const [openImproved, setOpenImproved] = useState<Record<number, boolean>>({});
  // 몰입형 오버레이 + 권한 프리프롬프트
  const [overlay, setOverlay] = useState<PermKind | null>(null);
  const [preprompt, setPreprompt] = useState<PermKind | null>(null);
  // 몰입형 원본은 answer 저장 전까지 ref_id=null pending 파일로 관리한다.
  const [pendingMedia, setPendingMediaState] = useState<PendingInterviewMedia | null>(null);
  const pendingMediaRef = useRef<PendingInterviewMedia | null>(null);
  const pendingFileIdsRef = useRef(new Set<number>());
  const protectedFileIdsRef = useRef(new Set<number>());
  const mediaUploadGenerationRef = useRef(0);
  const sessionGenerationRef = useRef(0);
  const mountedRef = useRef(true);
  const activeSessionIdRef = useRef(sessionId);
  const activeUploadAbortRef = useRef<AbortController | null>(null);
  const [mediaUploadGeneration, setMediaUploadGeneration] = useState<number | null>(null);
  const mediaUploading = mediaUploadGeneration != null;
  const [uncertainSubmission, setUncertainSubmission] = useState<PendingAnswerSubmission | null>(null);
  const [deletingMedia, setDeletingMedia] = useState<Record<string, boolean>>({});

  const scrollRef = useRef<HTMLDivElement>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  draftRef.current = draft;

  const toast = useCallback((msg: string) => {
    setToastMsg(msg);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToastMsg(null), 3200);
  }, []);

  const setPendingMedia = useCallback((next: PendingInterviewMedia | null) => {
    pendingMediaRef.current = next;
    setPendingMediaState(next);
  }, []);

  const cleanupPendingFile = useCallback(async (fileId: number, keepalive = false) => {
    if (protectedFileIdsRef.current.has(fileId)) return false;
    try {
      await deletePendingInterviewFile(fileId, keepalive);
      pendingFileIdsRef.current.delete(fileId);
      return true;
    } catch {
      // 재시도 가능한 ID를 집합에 남긴다. unmount에서 한 번 더 best-effort 정리한다.
      pendingFileIdsRef.current.add(fileId);
      return false;
    }
  }, []);

  const cleanupEligiblePendingFiles = useCallback((keepalive = false) => {
    for (const fileId of cleanupEligiblePendingFileIds(
      pendingFileIdsRef.current,
      protectedFileIdsRef.current,
    )) {
      void cleanupPendingFile(fileId, keepalive);
    }
  }, [cleanupPendingFile]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      mediaUploadGenerationRef.current += 1;
      sessionGenerationRef.current += 1;
      activeUploadAbortRef.current?.abort();
      activeUploadAbortRef.current = null;
      pendingMediaRef.current = null;
      cleanupEligiblePendingFiles(true);
      if (toastTimer.current) clearTimeout(toastTimer.current);
    };
  }, [cleanupEligiblePendingFiles]);

  useEffect(() => onAppLockState((locked) => {
    if (!locked) return;
    mediaUploadGenerationRef.current += 1;
    activeUploadAbortRef.current?.abort();
    activeUploadAbortRef.current = null;
    setMediaUploadGeneration(null);
    setOverlay(null);
    setPreprompt(null);
  }), []);

  const acceptCapturedMedia = useCallback(async ({
    sourceSessionId,
    questionId,
    kind,
    blob,
    format,
    transcript,
    voiceScore,
    visualScore,
  }: {
    sourceSessionId: number;
    questionId: number;
    kind: PendingInterviewMediaKind;
    blob: Blob | null;
    format: string;
    transcript: string;
    voiceScore: number | null;
    visualScore: number | null;
  }) => {
    if (sourceSessionId !== activeSessionIdRef.current) return;
    const nextDraft = capturedDraft(transcript);
    const expectedSessionId = sourceSessionId;
    const expectedSessionGeneration = sessionGenerationRef.current;
    const previous = pendingMediaRef.current;
    setSubmissionError(null);
    const generation = ++mediaUploadGenerationRef.current;
    const validation = validateCapturedMediaSize(blob?.size ?? 0);
    const invalidReason = validation.ok ? null : validation.reason;

    if (!blob || invalidReason) {
      if (!previous && nextDraft) {
        draftRef.current = nextDraft;
        setDraft(nextDraft);
      }
      const tooLarge = invalidReason === "TOO_LARGE";
      toast(previous
        ? tooLarge
          ? "새 원본이 9MB 안전 한도를 넘었습니다 — 이전 답변과 원본을 유지합니다"
          : "새 원본을 만들지 못했습니다 — 이전 답변과 원본을 유지합니다"
        : tooLarge
          ? "원본이 9MB 안전 한도를 넘었습니다 — 더 짧게 다시 녹화해 주세요"
          : "원본을 만들지 못했습니다 — 전사를 텍스트 답변으로 보존했습니다");
      return;
    }

    const controller = new AbortController();
    activeUploadAbortRef.current?.abort();
    activeUploadAbortRef.current = controller;
    setMediaUploadGeneration(generation);
    try {
      const extension = format === "mp4" ? "mp4" : "webm";
      const file = await uploadFile(blob, kind, {
        fileName: `${kind === "AUDIO" ? "voice" : "video"}-answer-${questionId}.${extension}`,
        refType: "INTERVIEW_ANSWER",
        signal: controller.signal,
      });
      pendingFileIdsRef.current.add(file.id);
      if (
        controller.signal.aborted
        || !mountedRef.current
        || generation !== mediaUploadGenerationRef.current
        || expectedSessionId !== activeSessionIdRef.current
        || expectedSessionGeneration !== sessionGenerationRef.current
      ) {
        await cleanupPendingFile(file.id, true);
        return;
      }
      // 새 전사와 새 원본을 같은 render batch에서 교체한다. 빈 STT면 직접 입력하도록 초안을 비운다.
      setPendingMedia({ questionId, kind, file, voiceScore, visualScore });
      draftRef.current = nextDraft;
      setDraft(nextDraft);
      if (previous && previous.file.id !== file.id) {
        // 새 원본 저장이 확정된 뒤에만 이전 원본을 정리해 업로드 실패 시 복구 가능성을 보존한다.
        void cleanupPendingFile(previous.file.id);
      }
      toast(nextDraft
        ? kind === "AUDIO"
          ? "전사·원본 저장 완료 — 확인 후 전송하세요"
          : "영상 분석·원본 저장 완료 — 확인 후 전송하세요"
        : "원본 저장 완료 — 답변 텍스트를 입력한 뒤 전송하세요");
    } catch {
      if (
        !controller.signal.aborted
        && mountedRef.current
        && generation === mediaUploadGenerationRef.current
        && expectedSessionId === activeSessionIdRef.current
        && expectedSessionGeneration === sessionGenerationRef.current
      ) {
        toast(previous
          ? "새 원본 저장에 실패했습니다 — 이전 답변과 원본을 유지합니다"
          : "원본 저장에 실패했습니다 — 다시 녹음하거나 텍스트로 제출해 주세요");
      }
    } finally {
      if (activeUploadAbortRef.current === controller) activeUploadAbortRef.current = null;
      if (mountedRef.current) {
        setMediaUploadGeneration((active) => settleMediaUploadGeneration(active, generation));
      }
    }
  }, [cleanupPendingFile, setPendingMedia, toast]);

  const discardPendingMedia = useCallback(async () => {
    const current = pendingMediaRef.current;
    if (!current || mediaUploading || scoring || protectedFileIdsRef.current.has(current.file.id)) return;
    mediaUploadGenerationRef.current += 1;
    activeUploadAbortRef.current?.abort();
    activeUploadAbortRef.current = null;
    setMediaUploadGeneration(null);
    setPendingMedia(null);
    const deleted = await cleanupPendingFile(current.file.id);
    toast(deleted ? "전송 대기 원본을 삭제했습니다" : "원본 삭제에 실패했습니다 — 페이지를 닫을 때 다시 정리합니다");
  }, [cleanupPendingFile, mediaUploading, scoring, setPendingMedia, toast]);

  useLayoutEffect(() => {
    if (activeSessionIdRef.current === sessionId) return;
    activeSessionIdRef.current = sessionId;
    sessionGenerationRef.current += 1;
    mediaUploadGenerationRef.current += 1;
    activeUploadAbortRef.current?.abort();
    activeUploadAbortRef.current = null;
    setPendingMedia(null);
    cleanupEligiblePendingFiles(true);
    draftRef.current = "";
    setDraft("");
    setSession(null);
    setItems([]);
    setLoading(true);
    setGenerating(false);
    setScoring(false);
    setSubmissionError(null);
    setUncertainSubmission(null);
    setOverlay(null);
    setPreprompt(null);
    setOpenModel({});
    setOpenImproved({});
    setDeletingMedia({});
    setMediaUploadGeneration(null);
    setToastMsg(null);
    if (toastTimer.current) clearTimeout(toastTimer.current);
  }, [cleanupEligiblePendingFiles, sessionId, setPendingMedia]);

  const scrollToEnd = useCallback(() => {
    requestAnimationFrame(() => {
      scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
    });
  }, []);

  const caseLabel = useMemo(() => {
    const c = session
      ? cases.applicationCases.find((x) => x.id === session.applicationCaseId)
      : undefined;
    return c ? `${c.companyName} · ${c.jobTitle}` : session ? `지원건 #${session.applicationCaseId}` : "";
  }, [session, cases.applicationCases]);

  const modeLabel = session ? getInterviewModeLabel(session.mode) : "";

  // 현재 답변 대상 = 아직 score 없는 첫 질문
  const currentQ = useMemo(() => {
    const scored = new Set(
      items.filter((it) => it.kind === "score").map((it) => (it as { s: ScoreInfo }).s.qid),
    );
    const q = items.find((it) => it.kind === "question" && !scored.has(it.q.id));
    return q && q.kind === "question" ? q.q : null;
  }, [items]);

  const answeredCount = useMemo(
    () => items.filter((it) => it.kind === "score").length,
    [items],
  );
  const questionCount = useMemo(
    () => items.filter((it) => it.kind === "question").length,
    [items],
  );

  /** questions + review 병합 → 스레드 재구성 (데스크탑 InterviewSession.reloadThread 와 동형). */
  const reload = useCallback(async (
    targetSessionId = sessionId,
    expectedGeneration = sessionGenerationRef.current,
  ) => {
    if (
      targetSessionId === activeSessionIdRef.current
      && expectedGeneration === sessionGenerationRef.current
    ) setLoading(true);
    try {
      const [qs, review] = await Promise.all([
        listSessionQuestions(targetSessionId),
        getSessionReview(targetSessionId).catch(() => null),
      ]);
      if (
        !mountedRef.current
        || targetSessionId !== activeSessionIdRef.current
        || expectedGeneration !== sessionGenerationRef.current
      ) return;
      const byQid = new Map(review?.items.map((it) => [it.questionId, it]) ?? []);
      const out: ThreadItem[] = [];
      for (const q of qs) {
        out.push({ kind: "question", q });
        const rv = byQid.get(q.id);
        if (rv?.answerText) {
          out.push({
            kind: "answer",
            qid: q.id,
            answerId: rv.answerId,
            text: rv.answerText,
            hasAudio: Boolean(rv.audioUrl),
            hasVideo: Boolean(rv.videoUrl),
            audioUrl: rv.audioUrl,
            videoUrl: rv.videoUrl,
          });
          out.push({
            kind: "score",
            s: {
              qid: q.id,
              score: rv.score ?? null,
              feedback: rv.feedback ?? null,
              improvedAnswer: rv.improvedAnswer ?? null,
              modelAnswer: rv.modelAnswer ?? null,
              voiceScore: null,
              visualScore: null,
            },
          });
        }
      }
      setItems(out);
    } catch {
      if (
        mountedRef.current
        && targetSessionId === activeSessionIdRef.current
        && expectedGeneration === sessionGenerationRef.current
      ) toast("세션을 불러오지 못했습니다");
    } finally {
      if (
        mountedRef.current
        && targetSessionId === activeSessionIdRef.current
        && expectedGeneration === sessionGenerationRef.current
      ) setLoading(false);
    }
  }, [sessionId, toast]);

  // 세션 메타 + 스레드 로드 + 이어받기 기록
  useEffect(() => {
    if (authLoading) return;
    if (!isAuthenticated) {
      navigate("/login");
      return;
    }
    if (!Number.isFinite(sessionId)) {
      navigate("/m/sessions");
      return;
    }
    const expectedGeneration = sessionGenerationRef.current;
    void (async () => {
      try {
        const page = await listInterviewSessions(0, 100);
        if (
          mountedRef.current
          && sessionId === activeSessionIdRef.current
          && expectedGeneration === sessionGenerationRef.current
        ) setSession(page.sessions.find((s) => s.id === sessionId) ?? null);
      } catch {
        /* 메타 실패해도 스레드는 시도 */
      }
      void markSessionResumed(sessionId).catch(() => undefined);
      await reload(sessionId, expectedGeneration);
    })();
  }, [authLoading, isAuthenticated, sessionId, navigate, reload]);

  useEffect(scrollToEnd, [items.length, scrollToEnd]);

  const isSubmissionCurrent = useCallback((submission: PendingAnswerSubmission) =>
    mountedRef.current
    && activeSessionIdRef.current === submission.sessionId
    && sessionGenerationRef.current === submission.sessionGeneration, []);

  const reconcileSubmission = useCallback(async (
    submission: PendingAnswerSubmission,
  ): Promise<SubmissionReconciliation> => {
    let allReadsAuthoritative = true;
    let authoritativeMisses = 0;
    for (const delayMs of SUBMISSION_RECONCILE_DELAYS_MS) {
      if (delayMs > 0) await new Promise<void>((resolve) => setTimeout(resolve, delayMs));
      if (!isSubmissionCurrent(submission)) return { status: "UNKNOWN" };
      // 장애 demo review는 운영 DB의 부재를 증명할 수 없다.
      if (isOutageFallbackActive()) {
        allReadsAuthoritative = false;
        continue;
      }
      try {
        const review = await getSessionReview(submission.sessionId);
        if (isOutageFallbackActive()) {
          allReadsAuthoritative = false;
          continue;
        }
        const media = submission.media;
        const item = findReconciledAnswer(review.items, {
          questionId: submission.questionId,
          answerText: submission.text,
          mediaKind: media?.kind,
          fileId: media?.file.id,
          contentUrl: media?.file.contentUrl,
        });
        if (item) return { status: "SAVED", item };
        authoritativeMisses += 1;
      } catch {
        allReadsAuthoritative = false;
      }
    }
    return concludeMissingSubmissionReconciliation(
      authoritativeMisses,
      SUBMISSION_RECONCILE_DELAYS_MS.length,
      !allReadsAuthoritative,
    ) === "NOT_SAVED" ? { status: "NOT_SAVED" } : { status: "UNKNOWN" };
  }, [isSubmissionCurrent]);

  const applyReconciledAnswer = useCallback((
    submission: PendingAnswerSubmission,
    review: SessionReviewItem,
  ) => {
    setItems((previousItems) => {
      const base = rollbackOptimisticSubmission(previousItems, submission.submissionId)
        .filter((item) => !(item.kind === "answer" && item.qid === submission.questionId))
        .filter((item) => !(item.kind === "score" && item.s.qid === submission.questionId));
      const out: ThreadItem[] = [];
      for (const item of base) {
        out.push(item);
        if (item.kind !== "question" || item.q.id !== submission.questionId) continue;
        out.push({
          kind: "answer",
          qid: submission.questionId,
          answerId: review.answerId,
          text: review.answerText ?? submission.text,
          hasAudio: Boolean(review.audioUrl),
          hasVideo: Boolean(review.videoUrl),
          audioUrl: review.audioUrl,
          videoUrl: review.videoUrl,
        });
        out.push({
          kind: "score",
          s: {
            qid: submission.questionId,
            score: review.score,
            feedback: review.feedback,
            improvedAnswer: review.improvedAnswer,
            modelAnswer: review.modelAnswer,
            voiceScore: submission.voiceScore,
            visualScore: submission.visualScore,
          },
        });
      }
      return out;
    });
  }, []);

  const settleReconciledSubmission = useCallback((
    submission: PendingAnswerSubmission,
    result: SubmissionReconciliation,
  ) => {
    const media = submission.media;
    if (result.status === "SAVED") {
      if (media) {
        protectedFileIdsRef.current.delete(media.file.id);
        pendingFileIdsRef.current.delete(media.file.id);
      }
      if (!isSubmissionCurrent(submission)) return;
      setUncertainSubmission(null);
      setSubmissionError(null);
      setPendingMedia(null);
      draftRef.current = "";
      setDraft("");
      applyReconciledAnswer(submission, result.item);
      toast("서버에서 이미 저장된 답변을 확인했습니다 — 다시 전송하지 않았습니다");
      haptic("medium");
      return;
    }

    if (result.status === "NOT_SAVED") {
      if (media) protectedFileIdsRef.current.delete(media.file.id);
      if (!isSubmissionCurrent(submission)) {
        if (media) void cleanupPendingFile(media.file.id, true);
        return;
      }
      setUncertainSubmission(null);
      if (media) setPendingMedia(media);
      setSubmissionError("서버에 저장되지 않은 것을 확인했습니다. 보존된 답변을 다시 보낼 수 있습니다.");
      toast("미저장을 확인했습니다 — 답변과 원본을 보존했습니다");
      return;
    }

    if (!isSubmissionCurrent(submission)) return;
    // 결과 불명 파일은 linked일 수 있으므로 pending cleanup과 X에서 계속 격리한다.
    setUncertainSubmission(submission);
    setPendingMedia(null);
    setSubmissionError("저장 여부를 확인할 수 없습니다. 중복 방지를 위해 재전송하지 말고 서버 복구 후 다시 확인해 주세요.");
    toast("답변 저장 여부 확인이 필요합니다 — 자동 재전송하지 않았습니다");
  }, [applyReconciledAnswer, cleanupPendingFile, isSubmissionCurrent, setPendingMedia, toast]);

  const retryUncertainReconciliation = useCallback(async () => {
    const submission = uncertainSubmission;
    if (!submission || scoring) return;
    setScoring(true);
    const result = await reconcileSubmission(submission);
    settleReconciledSubmission(submission, result);
    if (isSubmissionCurrent(submission)) setScoring(false);
  }, [isSubmissionCurrent, reconcileSubmission, scoring, settleReconciledSubmission, uncertainSubmission]);

  /** 답변 제출 → 채점 카드 (몰입형 점수 있으면 병기). */
  const send = async () => {
    const text = draft.trim();
    if (!text || !currentQ || scoring || mediaUploading || uncertainSubmission) return;
    haptic("light");
    const expectedSessionId = sessionId;
    const expectedSessionGeneration = sessionGenerationRef.current;
    const qid = currentQ.id;
    const selectedMedia = pendingMediaRef.current;
    if (selectedMedia && selectedMedia.questionId !== qid) {
      setPendingMedia(null);
      await cleanupPendingFile(selectedMedia.file.id);
      if (
        expectedSessionId !== activeSessionIdRef.current
        || expectedSessionGeneration !== sessionGenerationRef.current
      ) return;
    }
    const pending = selectedMedia?.questionId === qid ? selectedMedia : null;
    const hadAudio = pending?.kind === "AUDIO";
    const hadVideo = pending?.kind === "VIDEO";
    const voiceScore = pending?.voiceScore ?? null;
    const visualScore = pending?.visualScore ?? null;
    const mediaFields = pending
      ? buildPendingMediaAnswerFields(pending.kind, pending.file.id, pending.file.contentUrl)
      : {};
    const submissionId = `${qid}-${Date.now()}`;
    const submission: PendingAnswerSubmission = {
      sessionId: expectedSessionId,
      sessionGeneration: expectedSessionGeneration,
      questionId: qid,
      text,
      submissionId,
      media: pending,
      voiceScore,
      visualScore,
    };
    if (pending) {
      protectedFileIdsRef.current.add(pending.file.id);
      setPendingMedia(null);
    }
    setSubmissionError(null);
    draftRef.current = "";
    setDraft("");
    setScoring(true);
    setItems((prev) => [
      ...prev,
      {
        kind: "answer",
        qid,
        answerId: null,
        text,
        hasAudio: hadAudio,
        hasVideo: hadVideo,
        audioUrl: hadAudio ? pending?.file.contentUrl ?? null : null,
        videoUrl: hadVideo ? pending?.file.contentUrl ?? null : null,
        submissionId,
      },
      { kind: "scoring", submissionId },
    ]);
    try {
      const a = await submitAnswer(qid, { answerText: text, ...mediaFields });
      if (pending) {
        protectedFileIdsRef.current.delete(pending.file.id);
        pendingFileIdsRef.current.delete(pending.file.id);
      }
      if (!isSubmissionCurrent(submission)) return;
      setItems((prev) => [
        ...prev
          .filter((it) => !(it.kind === "scoring" && it.submissionId === submissionId))
          .map((it) => it.kind === "answer" && it.submissionId === submissionId
            ? {
                ...it,
                answerId: a.id,
                audioUrl: a.audioUrl,
                videoUrl: a.videoUrl,
                hasAudio: Boolean(a.audioUrl),
                hasVideo: Boolean(a.videoUrl),
                submissionId: undefined,
              }
            : it),
        {
          kind: "score",
          s: {
            qid,
            score: a.score,
            feedback: a.feedback,
            improvedAnswer: a.improvedAnswer,
            modelAnswer: null,
            voiceScore,
            visualScore,
          },
        },
      ]);
      setUncertainSubmission(null);
      toast(a.score != null ? `채점 완료 — ${a.score}점` : "답변이 저장되었습니다");
      haptic("medium");
    } catch (error) {
      if (isSubmissionCurrent(submission)) {
        setItems((prev) => rollbackOptimisticSubmission(prev, submissionId));
        const restored = restoreFailedDraft(draftRef.current, text);
        draftRef.current = restored;
        setDraft(restored);
      }
      if (classifySubmissionFailure(error) === "DEFINITE") {
        if (pending) protectedFileIdsRef.current.delete(pending.file.id);
        if (isSubmissionCurrent(submission)) {
          if (pending) setPendingMedia(pending);
          setSubmissionError("답변이 전송되지 않았습니다. 답변과 원본을 보존했습니다.");
          toast("답변 제출에 실패했습니다 — 입력 내용과 원본을 보존했습니다");
        } else if (pending) {
          void cleanupPendingFile(pending.file.id, true);
        }
      } else {
        const reconciliation = await reconcileSubmission(submission);
        settleReconciledSubmission(submission, reconciliation);
      }
    } finally {
      if (isSubmissionCurrent(submission)) setScoring(false);
    }
  };

  /** 마지막 채점 질문에 꼬리질문 1개. */
  const followUp = async () => {
    const expectedSessionId = sessionId;
    const expectedGeneration = sessionGenerationRef.current;
    const stillCurrent = () => mountedRef.current
      && activeSessionIdRef.current === expectedSessionId
      && sessionGenerationRef.current === expectedGeneration;
    const scoredItems = items.filter((it) => it.kind === "score") as { s: ScoreInfo }[];
    const last = scoredItems[scoredItems.length - 1];
    if (!last) return;
    try {
      const updated = await generateFollowUps(last.s.qid, { count: 1 });
      if (!stillCurrent()) return;
      const known = new Set(
        items.filter((it) => it.kind === "question").map((it) => (it as { q: InterviewQuestion }).q.id),
      );
      const fresh = updated.filter((q) => !known.has(q.id));
      if (fresh.length === 0) {
        toast("꼬리질문 생성에 실패했습니다");
        return;
      }
      setItems((prev) => [...prev, ...fresh.map((q) => ({ kind: "question" as const, q }))]);
      toast("꼬리질문이 도착했습니다");
    } catch {
      if (stillCurrent()) toast("꼬리질문 생성에 실패했습니다");
    }
  };

  const showModelAnswer = async (qid: number) => {
    const expectedSessionId = sessionId;
    const expectedGeneration = sessionGenerationRef.current;
    const stillCurrent = () => mountedRef.current
      && activeSessionIdRef.current === expectedSessionId
      && sessionGenerationRef.current === expectedGeneration;
    const has = items.some(
      (it) => it.kind === "score" && it.s.qid === qid && it.s.modelAnswer,
    );
    if (!has) {
      try {
        const { modelAnswer } = await getModelAnswer(qid);
        if (!stillCurrent()) return;
        setItems((prev) =>
          prev.map((it) =>
            it.kind === "score" && it.s.qid === qid ? { ...it, s: { ...it.s, modelAnswer } } : it,
          ),
        );
      } catch {
        if (stillCurrent()) toast("모범답안을 불러오지 못했습니다");
        return;
      }
    }
    if (!stillCurrent()) return;
    setOpenModel((prev) => ({ ...prev, [qid]: !prev[qid] }));
  };

  const removeStoredMedia = async (
    answerId: number,
    kind: PendingInterviewMediaKind,
  ) => {
    const expectedSessionId = sessionId;
    const expectedGeneration = sessionGenerationRef.current;
    const stillCurrent = () => mountedRef.current
      && activeSessionIdRef.current === expectedSessionId
      && sessionGenerationRef.current === expectedGeneration;
    const label = kind === "AUDIO" ? "음성" : "영상";
    if (!window.confirm(`${label} 원본을 삭제할까요? 삭제 후에는 원본 기반 재분석이 불가능합니다.`)) {
      return;
    }
    const key = `${answerId}-${kind}`;
    setDeletingMedia((prev) => ({ ...prev, [key]: true }));
    try {
      await deleteAnswerMedia(answerId, kind);
      if (!stillCurrent()) return;
      setItems((prev) => prev.map((item) => {
        if (item.kind !== "answer" || item.answerId !== answerId) return item;
        return kind === "AUDIO"
          ? { ...item, audioUrl: null, hasAudio: false }
          : { ...item, videoUrl: null, hasVideo: false };
      }));
      toast(`${label} 원본을 삭제했습니다 — 답변과 채점 결과는 유지됩니다`);
    } catch {
      if (stillCurrent()) toast(`${label} 원본 삭제에 실패했습니다`);
    } finally {
      if (stillCurrent()) setDeletingMedia((prev) => ({ ...prev, [key]: false }));
    }
  };

  const generate = async () => {
    if (!session) return;
    const expectedSessionId = sessionId;
    const expectedGeneration = sessionGenerationRef.current;
    const stillCurrent = () => mountedRef.current
      && activeSessionIdRef.current === expectedSessionId
      && sessionGenerationRef.current === expectedGeneration;
    setGenerating(true);
    try {
      await generateExpectedQuestions(sessionId, { mode: session.mode });
      if (!stillCurrent()) return;
      await reload(expectedSessionId, expectedGeneration);
    } catch {
      if (stillCurrent()) toast("질문 생성에 실패했습니다");
    } finally {
      if (stillCurrent()) setGenerating(false);
    }
  };

  const sendToDesktop = async () => {
    const expectedSessionId = sessionId;
    const expectedGeneration = sessionGenerationRef.current;
    const stillCurrent = () => mountedRef.current
      && activeSessionIdRef.current === expectedSessionId
      && sessionGenerationRef.current === expectedGeneration;
    haptic("light");
    try {
      await dispatchSessionToDesktop(sessionId);
      if (stillCurrent()) toast("데스크탑으로 보냈습니다 — PC 트레이 알림 확인 (30초 내)");
    } catch {
      if (stillCurrent()) toast("보내기에 실패했습니다");
    }
  };

  /** 마이크/카메라 진입 — 권한 프리프롬프트 게이트. */
  const openMedia = (kind: PermKind) => {
    if (!currentQ) {
      toast("대기 중인 질문이 없습니다");
      return;
    }
    haptic("light");
    if (isPrepromptAccepted(kind, consentScope)) setOverlay(kind);
    else setPreprompt(kind);
  };

  const qLabel = currentQ
    ? `Q${answeredCount + 1} / ${Math.max(questionCount, answeredCount + 1)}`
    : "";

  return (
    <div className="fixed inset-0 z-40 flex flex-col bg-background text-foreground">
      {/* 앰비언트 */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(320px 240px at 50% -60px, rgba(94,106,210,0.10), transparent 70%)",
        }}
      />
      {/* 앱바 */}
      <div
        className="relative flex h-[52px] shrink-0 items-center gap-2 border-b border-border px-3"
        style={{ marginTop: "env(safe-area-inset-top)" }}
      >
        <button
          onClick={() => navigate(-1)}
          className="flex size-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-accent hover:text-foreground"
          aria-label="뒤로"
        >
          <ArrowLeft className="size-5" />
        </button>
        <div className="min-w-0 flex-1">
          <div className="truncate text-[14px] font-semibold tracking-tight">{caseLabel || "면접 세션"}</div>
        </div>
        <span
          className={`rounded-full border px-2.5 py-0.5 text-[11px] ${
            currentQ
              ? "border-border bg-muted text-amber-700 dark:text-[#d6a24c]"
              : "border-border bg-muted text-emerald-700 dark:text-[#4cc38a]"
          }`}
        >
          {currentQ ? `${answeredCount}/${questionCount}` : "완료"}
        </span>
        <button
          onClick={() => void sendToDesktop()}
          className="flex size-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-accent hover:text-foreground"
          aria-label="데스크탑으로 보내기"
          title="데스크탑으로 보내기"
        >
          <Monitor className="size-[18px]" />
        </button>
      </div>

      {/* 스레드 */}
      <div ref={scrollRef} className="relative flex-1 overflow-y-auto px-4 pb-3">
        <div className="mx-auto max-w-xl">
          <div className="my-3 flex items-center gap-2.5 text-[10.5px] text-muted-foreground">
            <span className="h-px flex-1 bg-border" />
            <span>
              {modeLabel}
              {session ? ` · 지원건 #${session.applicationCaseId}` : ""}
            </span>
            <span className="h-px flex-1 bg-border" />
          </div>

          {loading && (
            <div className="flex items-center justify-center gap-3 py-14 text-[13px] text-muted-foreground">
              <span className="size-4 animate-spin rounded-full border-2 border-border border-t-primary" />
              세션을 불러오는 중
            </div>
          )}

          {!loading && items.length === 0 && (
            <div className="mt-8 rounded-2xl border border-border bg-card p-6 text-center shadow-sm">
              <div className="text-[14px] font-semibold">아직 질문이 없습니다</div>
              <p className="mt-1.5 text-[12px] leading-relaxed text-muted-foreground">
                공고·지원건 분석을 반영해 예상 질문을 생성합니다.
              </p>
              <AiChargeCostBadge
                featureType="INTERVIEW_QUESTION_GEN"
                className="mx-auto mt-3 bg-accent text-primary"
              />
              <button
                onClick={() => void generate()}
                disabled={generating}
                className="mt-4 rounded-[10px] bg-gradient-to-b from-[#7d88de] to-[#5E6AD2] px-5 py-2.5 text-[12.5px] font-semibold text-white shadow-[0_0_0_1px_rgba(94,106,210,0.5),0_4px_12px_rgba(94,106,210,0.3)] disabled:opacity-50"
              >
                {generating ? "생성 중…" : "예상 질문 생성"}
              </button>
            </div>
          )}

          {items.map((it, i) => {
            if (it.kind === "question") {
              return (
                <div key={`q-${it.q.id}`} className="my-4 flex gap-3">
                  <div className="flex size-7 shrink-0 items-center justify-center rounded-lg border border-[#5E6AD2]/30 bg-[#5E6AD2]/15 text-[#7d88de]">
                    <Sparkles className="size-3.5" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="mb-1 flex items-center gap-2 text-[10.5px] text-muted-foreground">
                      AI 면접관
                      <span
                        className={`rounded-full border px-2 py-px text-[10px] ${
                          it.q.parentQuestionId != null
                            ? "border-primary/30 bg-primary/10 text-primary dark:text-[#aab2ef]"
                            : "border-border bg-muted text-muted-foreground"
                        }`}
                      >
                        {it.q.parentQuestionId != null ? "꼬리질문" : it.q.questionType || "질문"}
                      </span>
                    </div>
                    <div className="text-[13.5px] leading-relaxed">{it.q.question}</div>
                  </div>
                </div>
              );
            }
            if (it.kind === "answer") {
              return (
                <div key={`a-${it.answerId ?? it.submissionId ?? i}`} className="my-4 flex gap-3">
                  <div className="flex size-7 shrink-0 items-center justify-center rounded-lg border border-border bg-muted text-[11px] font-bold text-muted-foreground">
                    나
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="mb-1 flex items-center gap-2 text-[10.5px] text-muted-foreground">
                      내 답변
                      {it.hasAudio && (
                        <span className="flex items-center gap-1 text-[10px]">
                          <Mic className="size-2.5" /> 음성
                        </span>
                      )}
                      {it.hasVideo && (
                        <span className="flex items-center gap-1 text-[10px]">
                          <Camera className="size-2.5" /> 영상
                        </span>
                      )}
                    </div>
                    <div className="rounded-xl border border-border bg-card px-3.5 py-2.5 text-[13px] leading-relaxed shadow-sm">
                      {it.text}
                    </div>
                    {it.answerId != null && (it.audioUrl || it.videoUrl) && (
                      <div className="mt-1.5 flex flex-wrap gap-1.5">
                        {it.audioUrl && (
                          <button
                            type="button"
                            onClick={() => void removeStoredMedia(it.answerId!, "AUDIO")}
                            disabled={deletingMedia[`${it.answerId}-AUDIO`]}
                            className="flex min-h-11 items-center gap-1.5 rounded-lg border border-red-200 px-3 text-[11px] font-medium text-red-700 hover:bg-red-50 disabled:opacity-50 dark:border-red-500/30 dark:text-red-300 dark:hover:bg-red-500/10"
                            aria-label="음성 원본 삭제"
                          >
                            <Trash2 className="size-2.5" />
                            {deletingMedia[`${it.answerId}-AUDIO`] ? "삭제 중…" : "음성 원본 삭제"}
                          </button>
                        )}
                        {it.videoUrl && (
                          <button
                            type="button"
                            onClick={() => void removeStoredMedia(it.answerId!, "VIDEO")}
                            disabled={deletingMedia[`${it.answerId}-VIDEO`]}
                            className="flex min-h-11 items-center gap-1.5 rounded-lg border border-red-200 px-3 text-[11px] font-medium text-red-700 hover:bg-red-50 disabled:opacity-50 dark:border-red-500/30 dark:text-red-300 dark:hover:bg-red-500/10"
                            aria-label="영상 원본 삭제"
                          >
                            <Trash2 className="size-2.5" />
                            {deletingMedia[`${it.answerId}-VIDEO`] ? "삭제 중…" : "영상 원본 삭제"}
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              );
            }
            if (it.kind === "scoring") {
              return (
                <div
                  key={`sc-${i}`}
                  className="my-3 flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 text-[12px] text-muted-foreground shadow-sm"
                >
                  <span className="size-3.5 animate-spin rounded-full border-2 border-border border-t-primary" />
                  답변 채점 중 — 내용·논리·직무적합 평가
                </div>
              );
            }
            const s = it.s;
            return (
              <div
                key={`s-${s.qid}-${i}`}
                className="my-3 overflow-hidden rounded-2xl border border-border bg-card shadow-sm dark:shadow-[0_2px_20px_rgba(0,0,0,0.35)]"
              >
                <div className="flex items-center gap-3.5 border-b border-border px-4 py-3">
                  <div className="flex size-11 shrink-0 items-center justify-center rounded-full border-2 border-[#5E6AD2] font-mono text-[14px] font-bold text-[#7d88de] shadow-[0_0_18px_rgba(94,106,210,0.2)]">
                    {s.score ?? "—"}
                  </div>
                  <div>
                    <div className="text-[13px] font-semibold">답변 평가</div>
                    <div className="mt-1.5 flex flex-wrap gap-1.5">
                      {s.voiceScore != null && (
                        <span className="flex items-center gap-1 rounded-full border border-primary/30 bg-primary/10 px-2 py-px text-[10px] text-primary dark:text-[#aab2ef]">
                          <Mic className="size-2.5" /> 전달력 {s.voiceScore}
                        </span>
                      )}
                      {s.visualScore != null && (
                        <span className="flex items-center gap-1 rounded-full border border-primary/30 bg-primary/10 px-2 py-px text-[10px] text-primary dark:text-[#aab2ef]">
                          <Camera className="size-2.5" /> 비언어 {s.visualScore}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
                {s.feedback && (
                  <div className="px-4 py-3 text-[12.5px] leading-relaxed text-foreground">{s.feedback}</div>
                )}
                {openImproved[s.qid] && s.improvedAnswer && (
                  <div className="mx-3.5 mb-2 rounded-lg bg-muted px-3 py-2.5">
                    <div className="mb-1 text-[11px] font-semibold text-[#4cc38a]">개선 답변 제안</div>
                    <div className="text-[12px] leading-relaxed text-muted-foreground">{s.improvedAnswer}</div>
                  </div>
                )}
                {openModel[s.qid] && s.modelAnswer && (
                  <div className="mx-3.5 mb-2 rounded-lg bg-muted px-3 py-2.5">
                    <div className="mb-1 text-[11px] font-semibold text-[#58a6ff]">모범답안</div>
                    <div className="text-[12px] leading-relaxed text-muted-foreground">{s.modelAnswer}</div>
                  </div>
                )}
                <AiChargeCostBadge
                  featureType="INTERVIEW_FOLLOWUP_GEN"
                  className="mx-3 mb-2 bg-accent text-primary"
                />
                <div className="flex flex-wrap gap-1.5 px-3.5 pb-3.5 pt-1">
                  <button
                    onClick={() => void showModelAnswer(s.qid)}
                    className="rounded-lg border border-border px-3 py-1.5 text-[11.5px] font-medium text-foreground hover:bg-accent"
                  >
                    {openModel[s.qid] ? "모범답안 접기" : "모범답안"}
                  </button>
                  {s.improvedAnswer && (
                    <button
                      onClick={() => setOpenImproved((p) => ({ ...p, [s.qid]: !p[s.qid] }))}
                      className="rounded-lg border border-border px-3 py-1.5 text-[11.5px] font-medium text-foreground hover:bg-accent"
                    >
                      {openImproved[s.qid] ? "개선 제안 접기" : "개선 제안"}
                    </button>
                  )}
                  <button
                    onClick={() => void followUp()}
                    className="rounded-lg border border-border px-3 py-1.5 text-[11.5px] font-medium text-foreground hover:bg-accent"
                  >
                    꼬리질문 받기
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* 입력 독 */}
      <div
        className="relative shrink-0 px-3 pt-1"
        style={{ paddingBottom: "calc(env(safe-area-inset-bottom) + 64px)" }}
      >
        <div className="mx-auto max-w-xl">
          {submissionError && (
            <div
              id="mobile-answer-submit-error"
              role="alert"
              className="mb-2 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-[12px] leading-relaxed text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300"
            >
              {submissionError}
              {uncertainSubmission && (
                <button
                  type="button"
                  onClick={() => void retryUncertainReconciliation()}
                  disabled={scoring}
                  className="mt-2 flex min-h-11 w-full items-center justify-center rounded-lg border border-red-300 bg-white px-3 text-[12px] font-semibold text-red-700 disabled:opacity-50 dark:border-red-400/40 dark:bg-transparent dark:text-red-200"
                >
                  {scoring ? "저장 여부 확인 중…" : "서버 저장 여부 다시 확인"}
                </button>
              )}
            </div>
          )}
          <div className="rounded-2xl border border-border bg-card p-3 shadow-lg focus-within:border-primary/40 focus-within:shadow-[0_0_0_3px_rgba(94,106,210,0.12)]">
            <AiChargeCostBadge
              featureType="INTERVIEW_ANSWER_EVAL"
              className="mb-2 bg-accent text-primary"
            />
            <textarea
              value={draft}
              onChange={(e) => {
                draftRef.current = e.target.value;
                setDraft(e.target.value);
              }}
              aria-describedby={submissionError ? "mobile-answer-submit-error" : undefined}
              placeholder={
                currentQ ? "답변을 입력하거나 마이크로 말하세요" : "모든 질문 완료 — 꼬리질문을 받아보세요"
              }
              disabled={!currentQ || scoring || mediaUploading || uncertainSubmission != null}
              rows={1}
              className="max-h-28 w-full resize-none bg-transparent px-1 pb-2 text-[13.5px] leading-relaxed text-foreground outline-none placeholder:text-muted-foreground disabled:opacity-50"
              onInput={(e) => {
                const el = e.currentTarget;
                el.style.height = "auto";
                el.style.height = `${Math.min(el.scrollHeight, 112)}px`;
              }}
            />
            <div className="flex flex-wrap items-center gap-1.5">
            <button
              onClick={() => openMedia("voice")}
              disabled={!currentQ || scoring || mediaUploading || uncertainSubmission != null}
              className="flex size-11 shrink-0 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-accent hover:text-foreground disabled:opacity-40"
              aria-label="음성 답변"
            >
              <Mic className="size-[17px]" />
            </button>
            <button
              onClick={() => openMedia("avatar")}
              disabled={!currentQ || scoring || mediaUploading || uncertainSubmission != null}
              className="flex size-11 shrink-0 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-accent hover:text-foreground disabled:opacity-40"
              aria-label="화상 답변"
            >
              <Camera className="size-[17px]" />
            </button>
            {mediaUploading && (
              <span className="flex min-h-11 items-center gap-1 rounded-full border border-border bg-muted px-3 text-[10px] text-muted-foreground" role="status">
                <span className="size-2.5 animate-spin rounded-full border border-border border-t-primary" /> 원본 저장 중
              </span>
            )}
            {pendingMedia && (
              <span className="flex min-h-11 items-center gap-1 rounded-full border border-primary/30 bg-primary/10 pl-3 text-[10px] text-primary dark:text-[#aab2ef]">
                <Check className="size-2.5" /> {pendingMedia.kind === "AUDIO" ? "음성" : "영상"} 원본 준비됨
                <button
                  type="button"
                  onClick={() => void discardPendingMedia()}
                  disabled={mediaUploading || scoring}
                  className="ml-0.5 flex size-11 shrink-0 items-center justify-center rounded-full hover:bg-primary/10 disabled:opacity-40"
                  aria-label="전송 대기 원본 제거"
                >
                  <X className="size-4" />
                </button>
              </span>
            )}
            <span className="flex min-h-11 items-center rounded-full border border-border bg-muted px-3 text-[10px] text-muted-foreground">
              {currentQ ? qLabel : "질문 없음"}
            </span>
            <button
              onClick={() => void send()}
              disabled={!currentQ || scoring || mediaUploading || uncertainSubmission != null || !draft.trim()}
              className="ml-auto flex size-11 shrink-0 items-center justify-center rounded-[9px] bg-gradient-to-b from-[#7d88de] to-[#5E6AD2] text-white shadow-[0_0_0_1px_rgba(94,106,210,0.5),0_3px_10px_rgba(94,106,210,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] disabled:opacity-40"
              aria-label="보내기"
            >
              <ArrowUp className="size-[17px]" />
            </button>
            </div>
          </div>
        </div>
      </div>

      {/* 토스트 */}
      {toastMsg && (
        <div
          role="status"
          aria-live="polite"
          className="absolute inset-x-4 z-[65] rounded-xl border border-border bg-card/95 px-4 py-3 text-[12.5px] text-foreground shadow-[0_12px_40px_rgba(0,0,0,0.25)] backdrop-blur-md"
          style={{ top: "calc(env(safe-area-inset-top) + 60px)" }}
        >
          {toastMsg}
        </div>
      )}

      {/* 권한 프리프롬프트 */}
      <PermissionPreprompt
        kind={preprompt ?? "voice"}
        scope={consentScope}
        open={preprompt !== null}
        onClose={() => setPreprompt(null)}
        onAllow={() => {
          const k = preprompt;
          setPreprompt(null);
          if (k) setOverlay(k);
        }}
      />

      {/* 몰입형 오버레이 */}
      {overlay === "voice" && currentQ && (
        <ImmersiveVoiceOverlay
          sessionId={sessionId}
          questionText={currentQ.question}
          questionLabel={qLabel}
          modeLabel={modeLabel}
          onClose={() => setOverlay(null)}
          onResult={({ transcript, voiceScore, audioBlob, audioFormat, captureError }) => {
            setOverlay(null);
            if (captureError === "AUDIO_TOO_LARGE") {
              if (!pendingMediaRef.current && transcript.trim()) {
                const text = capturedDraft(transcript);
                draftRef.current = text;
                setDraft(text);
              }
              toast("음성이 9MB 안전 한도를 넘었습니다 — 3분보다 짧게 다시 녹음해 주세요");
              return;
            }
            if (transcript.trim() || (audioBlob?.size ?? 0) > 0) {
              void acceptCapturedMedia({
                sourceSessionId: sessionId,
                questionId: currentQ.id,
                kind: "AUDIO",
                blob: audioBlob,
                format: audioFormat,
                transcript,
                voiceScore,
                visualScore: null,
              });
            } else {
              toast("음성이 인식되지 않았습니다 — 텍스트로 입력해 주세요");
            }
          }}
        />
      )}
      {overlay === "avatar" && currentQ && (
        <ImmersiveAvatarOverlay
          sessionId={sessionId}
          questionText={currentQ.question}
          questionLabel={qLabel}
          onClose={() => setOverlay(null)}
          onResult={({ transcript, voiceScore, visualScore, videoBlob, videoFormat, captureError }) => {
            setOverlay(null);
            if (captureError === "VIDEO_TOO_LARGE") {
              if (!pendingMediaRef.current && transcript.trim()) {
                const text = capturedDraft(transcript);
                draftRef.current = text;
                setDraft(text);
              }
              toast("영상이 9MB 안전 한도를 넘었습니다 — 45초보다 짧게 다시 녹화해 주세요");
              return;
            }
            if (transcript.trim() || (videoBlob?.size ?? 0) > 0) {
              void acceptCapturedMedia({
                sourceSessionId: sessionId,
                questionId: currentQ.id,
                kind: "VIDEO",
                blob: videoBlob,
                format: videoFormat,
                transcript,
                voiceScore,
                visualScore,
              });
            } else {
              toast("음성이 인식되지 않았습니다 — 텍스트로 입력해 주세요");
            }
          }}
        />
      )}
    </div>
  );
}
