import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { ArrowLeft, ArrowUp, Camera, Check, Mic, Monitor, Sparkles, Trash2, X } from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
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
import type { FileAsset, InterviewQuestion, InterviewSession } from "../types/interview";
import {
  buildPendingMediaAnswerFields,
  restoreFailedDraft,
  rollbackOptimisticSubmission,
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

export function MobileSessionThreadPage() {
  const { id } = useParams();
  const sessionId = Number(id);
  const navigate = useNavigate();
  const { isAuthenticated, loading: authLoading } = useAuth();
  const cases = useApplicationCases(isAuthenticated);

  const [session, setSession] = useState<InterviewSession | null>(null);
  const [items, setItems] = useState<ThreadItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [draft, setDraft] = useState("");
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
  const mediaUploadGenerationRef = useRef(0);
  const mountedRef = useRef(true);
  const activeSessionIdRef = useRef(sessionId);
  const [mediaUploading, setMediaUploading] = useState(false);
  const [deletingMedia, setDeletingMedia] = useState<Record<string, boolean>>({});

  const scrollRef = useRef<HTMLDivElement>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

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

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      mediaUploadGenerationRef.current += 1;
      pendingMediaRef.current = null;
      for (const fileId of pendingFileIdsRef.current) {
        void deletePendingInterviewFile(fileId, true)
          .then(() => pendingFileIdsRef.current.delete(fileId))
          .catch(() => undefined);
      }
    };
  }, []);

  const acceptCapturedMedia = useCallback(async ({
    questionId,
    kind,
    blob,
    format,
    transcript,
    voiceScore,
    visualScore,
  }: {
    questionId: number;
    kind: PendingInterviewMediaKind;
    blob: Blob | null;
    format: string;
    transcript: string;
    voiceScore: number | null;
    visualScore: number | null;
  }) => {
    const normalizedTranscript = transcript.trim();
    // STT가 비어도 원본은 보존해 사용자가 텍스트를 직접 입력한 뒤 함께 제출할 수 있다.
    // 이미 작성한 초안이 있다면 빈 전사 결과로 덮어쓰지 않는다.
    setDraft((current) => normalizedTranscript || current);
    setSubmissionError(null);
    const generation = ++mediaUploadGenerationRef.current;
    setMediaUploading(true);

    const previous = pendingMediaRef.current;

    if (!blob || blob.size === 0) {
      if (mountedRef.current && generation === mediaUploadGenerationRef.current) {
        setMediaUploading(false);
        toast(previous
          ? "새 원본을 만들지 못했습니다 — 이전 원본을 유지합니다"
          : "원본을 만들지 못했습니다 — 다시 녹음하거나 텍스트로 제출해 주세요");
      }
      return;
    }

    try {
      const extension = format === "mp4" ? "mp4" : "webm";
      const file = await uploadFile(blob, kind, {
        fileName: `${kind === "AUDIO" ? "voice" : "video"}-answer-${questionId}.${extension}`,
        refType: "INTERVIEW_ANSWER",
      });
      pendingFileIdsRef.current.add(file.id);
      if (!mountedRef.current || generation !== mediaUploadGenerationRef.current) {
        await cleanupPendingFile(file.id, true);
        return;
      }
      setPendingMedia({ questionId, kind, file, voiceScore, visualScore });
      if (previous && previous.file.id !== file.id) {
        // 새 원본 저장이 확정된 뒤에만 이전 원본을 정리해 업로드 실패 시 복구 가능성을 보존한다.
        void cleanupPendingFile(previous.file.id);
      }
      toast(normalizedTranscript
        ? kind === "AUDIO"
          ? "전사·원본 저장 완료 — 확인 후 전송하세요"
          : "영상 분석·원본 저장 완료 — 확인 후 전송하세요"
        : "원본 저장 완료 — 답변 텍스트를 입력한 뒤 전송하세요");
    } catch {
      if (mountedRef.current && generation === mediaUploadGenerationRef.current) {
        toast(previous
          ? "새 원본 저장에 실패했습니다 — 이전 원본을 유지합니다"
          : "원본 저장에 실패했습니다 — 다시 녹음하거나 텍스트로 제출해 주세요");
      }
    } finally {
      if (mountedRef.current && generation === mediaUploadGenerationRef.current) {
        setMediaUploading(false);
      }
    }
  }, [cleanupPendingFile, setPendingMedia, toast]);

  const discardPendingMedia = useCallback(async () => {
    const current = pendingMediaRef.current;
    if (!current) return;
    mediaUploadGenerationRef.current += 1;
    setPendingMedia(null);
    const deleted = await cleanupPendingFile(current.file.id);
    toast(deleted ? "전송 대기 원본을 삭제했습니다" : "원본 삭제에 실패했습니다 — 페이지를 닫을 때 다시 정리합니다");
  }, [cleanupPendingFile, setPendingMedia, toast]);

  useEffect(() => {
    if (activeSessionIdRef.current === sessionId) return;
    activeSessionIdRef.current = sessionId;
    mediaUploadGenerationRef.current += 1;
    setPendingMedia(null);
    for (const fileId of pendingFileIdsRef.current) {
      void cleanupPendingFile(fileId, true);
    }
    setMediaUploading(false);
  }, [cleanupPendingFile, sessionId, setPendingMedia]);

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
  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const [qs, review] = await Promise.all([
        listSessionQuestions(sessionId),
        getSessionReview(sessionId).catch(() => null),
      ]);
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
      toast("세션을 불러오지 못했습니다");
    } finally {
      setLoading(false);
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
    void (async () => {
      try {
        const page = await listInterviewSessions(0, 100);
        setSession(page.sessions.find((s) => s.id === sessionId) ?? null);
      } catch {
        /* 메타 실패해도 스레드는 시도 */
      }
      void markSessionResumed(sessionId).catch(() => undefined);
      await reload();
    })();
  }, [authLoading, isAuthenticated, sessionId, navigate, reload]);

  useEffect(scrollToEnd, [items.length, scrollToEnd]);

  /** 답변 제출 → 채점 카드 (몰입형 점수 있으면 병기). */
  const send = async () => {
    const text = draft.trim();
    if (!text || !currentQ || scoring || mediaUploading) return;
    haptic("light");
    const qid = currentQ.id;
    const selectedMedia = pendingMediaRef.current;
    if (selectedMedia && selectedMedia.questionId !== qid) {
      setPendingMedia(null);
      await cleanupPendingFile(selectedMedia.file.id);
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
    setSubmissionError(null);
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
      if (pending) {
        // 서버 transaction이 file_asset을 answer.id로 연결했으므로 pending 정리 대상에서 제외한다.
        pendingFileIdsRef.current.delete(pending.file.id);
        if (pendingMediaRef.current?.file.id === pending.file.id) setPendingMedia(null);
      }
      toast(a.score != null ? `채점 완료 — ${a.score}점` : "답변이 저장되었습니다");
      haptic("medium");
    } catch {
      setItems((prev) => rollbackOptimisticSubmission(prev, submissionId));
      setDraft((current) => restoreFailedDraft(current, text));
      setSubmissionError("전송되지 않았습니다. 답변을 보존했어요. 연결을 확인한 뒤 다시 보내주세요.");
      toast("답변 제출에 실패했습니다 — 입력 내용을 보존했습니다");
    } finally {
      setScoring(false);
    }
  };

  /** 마지막 채점 질문에 꼬리질문 1개. */
  const followUp = async () => {
    const scoredItems = items.filter((it) => it.kind === "score") as { s: ScoreInfo }[];
    const last = scoredItems[scoredItems.length - 1];
    if (!last) return;
    try {
      const updated = await generateFollowUps(last.s.qid, { count: 1 });
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
      toast("꼬리질문 생성에 실패했습니다");
    }
  };

  const showModelAnswer = async (qid: number) => {
    const has = items.some(
      (it) => it.kind === "score" && it.s.qid === qid && it.s.modelAnswer,
    );
    if (!has) {
      try {
        const { modelAnswer } = await getModelAnswer(qid);
        setItems((prev) =>
          prev.map((it) =>
            it.kind === "score" && it.s.qid === qid ? { ...it, s: { ...it.s, modelAnswer } } : it,
          ),
        );
      } catch {
        toast("모범답안을 불러오지 못했습니다");
        return;
      }
    }
    setOpenModel((prev) => ({ ...prev, [qid]: !prev[qid] }));
  };

  const removeStoredMedia = async (
    answerId: number,
    kind: PendingInterviewMediaKind,
  ) => {
    const label = kind === "AUDIO" ? "음성" : "영상";
    if (!window.confirm(`${label} 원본을 삭제할까요? 삭제 후에는 원본 기반 재분석이 불가능합니다.`)) {
      return;
    }
    const key = `${answerId}-${kind}`;
    setDeletingMedia((prev) => ({ ...prev, [key]: true }));
    try {
      await deleteAnswerMedia(answerId, kind);
      setItems((prev) => prev.map((item) => {
        if (item.kind !== "answer" || item.answerId !== answerId) return item;
        return kind === "AUDIO"
          ? { ...item, audioUrl: null, hasAudio: false }
          : { ...item, videoUrl: null, hasVideo: false };
      }));
      toast(`${label} 원본을 삭제했습니다 — 답변과 채점 결과는 유지됩니다`);
    } catch {
      toast(`${label} 원본 삭제에 실패했습니다`);
    } finally {
      setDeletingMedia((prev) => ({ ...prev, [key]: false }));
    }
  };

  const generate = async () => {
    if (!session) return;
    setGenerating(true);
    try {
      await generateExpectedQuestions(sessionId, { mode: session.mode });
      await reload();
    } catch {
      toast("질문 생성에 실패했습니다");
    } finally {
      setGenerating(false);
    }
  };

  const sendToDesktop = async () => {
    haptic("light");
    try {
      await dispatchSessionToDesktop(sessionId);
      toast("데스크탑으로 보냈습니다 — PC 트레이 알림 확인 (30초 내)");
    } catch {
      toast("보내기에 실패했습니다");
    }
  };

  /** 마이크/카메라 진입 — 권한 프리프롬프트 게이트. */
  const openMedia = (kind: PermKind) => {
    if (!currentQ) {
      toast("대기 중인 질문이 없습니다");
      return;
    }
    haptic("light");
    if (isPrepromptAccepted(kind)) setOverlay(kind);
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
                            className="flex items-center gap-1 rounded-md border border-red-200 px-2 py-1 text-[10px] text-red-600 hover:bg-red-50 disabled:opacity-50 dark:border-red-500/30 dark:text-red-300 dark:hover:bg-red-500/10"
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
                            className="flex items-center gap-1 rounded-md border border-red-200 px-2 py-1 text-[10px] text-red-600 hover:bg-red-50 disabled:opacity-50 dark:border-red-500/30 dark:text-red-300 dark:hover:bg-red-500/10"
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
            </div>
          )}
          <div className="rounded-2xl border border-border bg-card p-3 shadow-lg focus-within:border-primary/40 focus-within:shadow-[0_0_0_3px_rgba(94,106,210,0.12)]">
            <AiChargeCostBadge
              featureType="INTERVIEW_ANSWER_EVAL"
              className="mb-2 bg-accent text-primary"
            />
            <textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              aria-describedby={submissionError ? "mobile-answer-submit-error" : undefined}
              placeholder={
                currentQ ? "답변을 입력하거나 마이크로 말하세요" : "모든 질문 완료 — 꼬리질문을 받아보세요"
              }
              disabled={!currentQ || scoring}
              rows={1}
              className="max-h-28 w-full resize-none bg-transparent px-1 pb-2 text-[13.5px] leading-relaxed text-foreground outline-none placeholder:text-muted-foreground disabled:opacity-50"
              onInput={(e) => {
                const el = e.currentTarget;
                el.style.height = "auto";
                el.style.height = `${Math.min(el.scrollHeight, 112)}px`;
              }}
            />
            <div className="flex items-center gap-1">
            <button
              onClick={() => openMedia("voice")}
              disabled={!currentQ || scoring || mediaUploading}
              className="flex size-8 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-accent hover:text-foreground disabled:opacity-40"
              aria-label="음성 답변"
            >
              <Mic className="size-[17px]" />
            </button>
            <button
              onClick={() => openMedia("avatar")}
              disabled={!currentQ || scoring || mediaUploading}
              className="flex size-8 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-accent hover:text-foreground disabled:opacity-40"
              aria-label="화상 답변"
            >
              <Camera className="size-[17px]" />
            </button>
            {mediaUploading && (
              <span className="flex items-center gap-1 rounded-full border border-border bg-muted px-2 py-0.5 text-[10px] text-muted-foreground">
                <span className="size-2.5 animate-spin rounded-full border border-border border-t-primary" /> 원본 저장 중
              </span>
            )}
            {pendingMedia && (
              <span className="flex items-center gap-1 rounded-full border border-primary/30 bg-primary/10 px-2 py-0.5 text-[10px] text-primary dark:text-[#aab2ef]">
                <Check className="size-2.5" /> {pendingMedia.kind === "AUDIO" ? "음성" : "영상"} 원본 준비됨
                <button
                  type="button"
                  onClick={() => void discardPendingMedia()}
                  className="ml-0.5 rounded-full p-0.5 hover:bg-primary/10"
                  aria-label="전송 대기 원본 제거"
                >
                  <X className="size-2.5" />
                </button>
              </span>
            )}
            <span className="ml-1 rounded-full border border-border bg-muted px-2 py-0.5 text-[10px] text-muted-foreground">
              {currentQ ? qLabel : "질문 없음"}
            </span>
            <button
              onClick={() => void send()}
              disabled={!currentQ || scoring || mediaUploading || !draft.trim()}
              className="ml-auto flex size-[34px] items-center justify-center rounded-[9px] bg-gradient-to-b from-[#7d88de] to-[#5E6AD2] text-white shadow-[0_0_0_1px_rgba(94,106,210,0.5),0_3px_10px_rgba(94,106,210,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] disabled:opacity-40"
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
          className="absolute inset-x-4 z-[65] rounded-xl border border-border bg-card/95 px-4 py-3 text-[12.5px] text-foreground shadow-[0_12px_40px_rgba(0,0,0,0.25)] backdrop-blur-md"
          style={{ top: "calc(env(safe-area-inset-top) + 60px)" }}
        >
          {toastMsg}
        </div>
      )}

      {/* 권한 프리프롬프트 */}
      <PermissionPreprompt
        kind={preprompt ?? "voice"}
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
          onResult={({ transcript, voiceScore, audioBlob, audioFormat }) => {
            setOverlay(null);
            if (transcript.trim() || (audioBlob?.size ?? 0) > 0) {
              void acceptCapturedMedia({
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
          onResult={({ transcript, voiceScore, visualScore, videoBlob, videoFormat }) => {
            setOverlay(null);
            if (transcript.trim() || (videoBlob?.size ?? 0) > 0) {
              void acceptCapturedMedia({
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
