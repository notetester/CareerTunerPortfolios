import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { ArrowLeft, ArrowUp, Camera, Check, Mic, Monitor, Sparkles } from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { haptic } from "@/platform/haptics";
import { useApplicationCases } from "@/features/applications/hooks/useApplicationCases";
import {
  dispatchSessionToDevices,
  generateExpectedQuestions,
  generateFollowUps,
  getModelAnswer,
  getSessionReview,
  listInterviewSessions,
  listSessionQuestions,
  markSessionResumed,
  submitAnswer,
} from "../api/interviewApi";
import { getInterviewModeLabel } from "../types/interview";
import type { InterviewQuestion, InterviewSession } from "../types/interview";
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
type ThreadItem =
  | { kind: "question"; q: InterviewQuestion }
  | { kind: "answer"; text: string; hasAudio: boolean }
  | { kind: "score"; s: ScoreInfo }
  | { kind: "scoring" };

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
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const [openModel, setOpenModel] = useState<Record<number, boolean>>({});
  const [openImproved, setOpenImproved] = useState<Record<number, boolean>>({});
  // 몰입형 오버레이 + 권한 프리프롬프트
  const [overlay, setOverlay] = useState<PermKind | null>(null);
  const [preprompt, setPreprompt] = useState<PermKind | null>(null);
  // 몰입형에서 온 전달력/비언어 점수 — 다음 제출 시 점수 카드에 병기
  const pendingVoiceScore = useRef<number | null>(null);
  const pendingVisualScore = useRef<number | null>(null);
  const [hasVoicePending, setHasVoicePending] = useState(false);

  const scrollRef = useRef<HTMLDivElement>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const toast = useCallback((msg: string) => {
    setToastMsg(msg);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToastMsg(null), 3200);
  }, []);

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
          out.push({ kind: "answer", text: rv.answerText, hasAudio: false });
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
    if (!text || !currentQ || scoring) return;
    haptic("light");
    const qid = currentQ.id;
    const hadAudio = hasVoicePending;
    setDraft("");
    setScoring(true);
    setItems((prev) => [...prev, { kind: "answer", text, hasAudio: hadAudio }, { kind: "scoring" }]);
    try {
      const a = await submitAnswer(qid, { answerText: text });
      setItems((prev) => [
        ...prev.filter((it) => it.kind !== "scoring"),
        {
          kind: "score",
          s: {
            qid,
            score: a.score,
            feedback: a.feedback,
            improvedAnswer: a.improvedAnswer,
            modelAnswer: null,
            voiceScore: pendingVoiceScore.current,
            visualScore: pendingVisualScore.current,
          },
        },
      ]);
      toast(a.score != null ? `채점 완료 — ${a.score}점` : "답변이 저장되었습니다");
      haptic("medium");
    } catch {
      setItems((prev) => prev.filter((it) => it.kind !== "scoring"));
      toast("답변 제출에 실패했습니다");
    } finally {
      pendingVoiceScore.current = null;
      pendingVisualScore.current = null;
      setHasVoicePending(false);
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
      await dispatchSessionToDevices(sessionId);
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
    <div className="fixed inset-0 z-40 flex flex-col bg-[#050506] text-[#EDEDEF]">
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
        className="relative flex h-[52px] shrink-0 items-center gap-2 border-b border-white/[0.06] px-3"
        style={{ marginTop: "env(safe-area-inset-top)" }}
      >
        <button
          onClick={() => navigate(-1)}
          className="flex size-9 items-center justify-center rounded-lg text-[#8A8F98]"
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
              ? "border-white/[0.06] bg-white/[0.05] text-[#d6a24c]"
              : "border-white/[0.06] bg-white/[0.05] text-[#4cc38a]"
          }`}
        >
          {currentQ ? `${answeredCount}/${questionCount}` : "완료"}
        </span>
        <button
          onClick={() => void sendToDesktop()}
          className="flex size-9 items-center justify-center rounded-lg text-[#8A8F98]"
          aria-label="데스크탑으로 보내기"
          title="데스크탑으로 보내기"
        >
          <Monitor className="size-[18px]" />
        </button>
      </div>

      {/* 스레드 */}
      <div ref={scrollRef} className="relative flex-1 overflow-y-auto px-4 pb-3">
        <div className="mx-auto max-w-xl">
          <div className="my-3 flex items-center gap-2.5 text-[10.5px] text-[#8A8F98]">
            <span className="h-px flex-1 bg-white/[0.06]" />
            <span>
              {modeLabel}
              {session ? ` · 지원건 #${session.applicationCaseId}` : ""}
            </span>
            <span className="h-px flex-1 bg-white/[0.06]" />
          </div>

          {loading && (
            <div className="flex items-center justify-center gap-3 py-14 text-[13px] text-[#8A8F98]">
              <span className="size-4 animate-spin rounded-full border-2 border-white/10 border-t-[#5E6AD2]" />
              세션을 불러오는 중
            </div>
          )}

          {!loading && items.length === 0 && (
            <div className="mt-8 rounded-2xl border border-white/[0.06] bg-white/[0.04] p-6 text-center">
              <div className="text-[14px] font-semibold">아직 질문이 없습니다</div>
              <p className="mt-1.5 text-[12px] leading-relaxed text-[#8A8F98]">
                공고·지원건 분석을 반영해 예상 질문을 생성합니다.
              </p>
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
                    <div className="mb-1 flex items-center gap-2 text-[10.5px] text-[#8A8F98]">
                      AI 면접관
                      <span
                        className={`rounded-full border px-2 py-px text-[10px] ${
                          it.q.parentQuestionId != null
                            ? "border-[#5E6AD2]/30 bg-[#5E6AD2]/15 text-[#aab2ef]"
                            : "border-white/[0.06] bg-white/[0.05] text-[#8A8F98]"
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
                <div key={`a-${i}`} className="my-4 flex gap-3">
                  <div className="flex size-7 shrink-0 items-center justify-center rounded-lg border border-white/[0.06] bg-white/[0.05] text-[11px] font-bold text-[#8A8F98]">
                    나
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="mb-1 flex items-center gap-2 text-[10.5px] text-[#8A8F98]">
                      내 답변
                      {it.hasAudio && (
                        <span className="flex items-center gap-1 text-[10px]">
                          <Mic className="size-2.5" /> 음성
                        </span>
                      )}
                    </div>
                    <div className="rounded-xl border border-white/[0.06] bg-white/[0.04] px-3.5 py-2.5 text-[13px] leading-relaxed shadow-[inset_0_1px_0_rgba(255,255,255,0.04)]">
                      {it.text}
                    </div>
                  </div>
                </div>
              );
            }
            if (it.kind === "scoring") {
              return (
                <div
                  key={`sc-${i}`}
                  className="my-3 flex items-center gap-3 rounded-xl border border-white/[0.06] bg-white/[0.04] px-4 py-3 text-[12px] text-[#8A8F98]"
                >
                  <span className="size-3.5 animate-spin rounded-full border-2 border-white/10 border-t-[#5E6AD2]" />
                  답변 채점 중 — 내용·논리·직무적합 평가
                </div>
              );
            }
            const s = it.s;
            return (
              <div
                key={`s-${s.qid}-${i}`}
                className="my-3 overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.04] shadow-[0_0_0_1px_rgba(255,255,255,0.02),0_2px_20px_rgba(0,0,0,0.35),inset_0_1px_0_rgba(255,255,255,0.05)]"
              >
                <div className="flex items-center gap-3.5 border-b border-white/[0.06] px-4 py-3">
                  <div className="flex size-11 shrink-0 items-center justify-center rounded-full border-2 border-[#5E6AD2] font-mono text-[14px] font-bold text-[#7d88de] shadow-[0_0_18px_rgba(94,106,210,0.2)]">
                    {s.score ?? "—"}
                  </div>
                  <div>
                    <div className="text-[13px] font-semibold">답변 평가</div>
                    <div className="mt-1.5 flex flex-wrap gap-1.5">
                      {s.voiceScore != null && (
                        <span className="flex items-center gap-1 rounded-full border border-[#5E6AD2]/30 bg-[#5E6AD2]/15 px-2 py-px text-[10px] text-[#aab2ef]">
                          <Mic className="size-2.5" /> 전달력 {s.voiceScore}
                        </span>
                      )}
                      {s.visualScore != null && (
                        <span className="flex items-center gap-1 rounded-full border border-[#5E6AD2]/30 bg-[#5E6AD2]/15 px-2 py-px text-[10px] text-[#aab2ef]">
                          <Camera className="size-2.5" /> 비언어 {s.visualScore}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
                {s.feedback && (
                  <div className="px-4 py-3 text-[12.5px] leading-relaxed text-[#EDEDEF]">{s.feedback}</div>
                )}
                {openImproved[s.qid] && s.improvedAnswer && (
                  <div className="mx-3.5 mb-2 rounded-lg bg-[#050506] px-3 py-2.5">
                    <div className="mb-1 text-[11px] font-semibold text-[#4cc38a]">개선 답변 제안</div>
                    <div className="text-[12px] leading-relaxed text-[#8A8F98]">{s.improvedAnswer}</div>
                  </div>
                )}
                {openModel[s.qid] && s.modelAnswer && (
                  <div className="mx-3.5 mb-2 rounded-lg bg-[#050506] px-3 py-2.5">
                    <div className="mb-1 text-[11px] font-semibold text-[#58a6ff]">모범답안</div>
                    <div className="text-[12px] leading-relaxed text-[#8A8F98]">{s.modelAnswer}</div>
                  </div>
                )}
                <div className="flex flex-wrap gap-1.5 px-3.5 pb-3.5 pt-1">
                  <button
                    onClick={() => void showModelAnswer(s.qid)}
                    className="rounded-lg border border-white/[0.06] px-3 py-1.5 text-[11.5px] font-medium text-[#EDEDEF]"
                  >
                    {openModel[s.qid] ? "모범답안 접기" : "모범답안"}
                  </button>
                  {s.improvedAnswer && (
                    <button
                      onClick={() => setOpenImproved((p) => ({ ...p, [s.qid]: !p[s.qid] }))}
                      className="rounded-lg border border-white/[0.06] px-3 py-1.5 text-[11.5px] font-medium text-[#EDEDEF]"
                    >
                      {openImproved[s.qid] ? "개선 제안 접기" : "개선 제안"}
                    </button>
                  )}
                  <button
                    onClick={() => void followUp()}
                    className="rounded-lg border border-white/[0.06] px-3 py-1.5 text-[11.5px] font-medium text-[#EDEDEF]"
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
        <div className="mx-auto max-w-xl rounded-2xl border border-white/[0.06] bg-[#0a0a0c] p-3 shadow-[0_0_0_1px_rgba(255,255,255,0.02),0_8px_32px_rgba(0,0,0,0.4),inset_0_1px_0_rgba(255,255,255,0.04)] focus-within:border-[#5E6AD2]/40 focus-within:shadow-[0_0_0_3px_rgba(94,106,210,0.12)]">
          <textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder={
              currentQ ? "답변을 입력하거나 마이크로 말하세요" : "모든 질문 완료 — 꼬리질문을 받아보세요"
            }
            disabled={!currentQ || scoring}
            rows={1}
            className="max-h-28 w-full resize-none bg-transparent px-1 pb-2 text-[13.5px] leading-relaxed text-[#EDEDEF] outline-none placeholder:text-[#8A8F98] disabled:opacity-50"
            onInput={(e) => {
              const el = e.currentTarget;
              el.style.height = "auto";
              el.style.height = `${Math.min(el.scrollHeight, 112)}px`;
            }}
          />
          <div className="flex items-center gap-1">
            <button
              onClick={() => openMedia("voice")}
              disabled={!currentQ || scoring}
              className="flex size-8 items-center justify-center rounded-lg text-[#8A8F98] transition-colors hover:bg-white/[0.06] hover:text-[#EDEDEF] disabled:opacity-40"
              aria-label="음성 답변"
            >
              <Mic className="size-[17px]" />
            </button>
            <button
              onClick={() => openMedia("avatar")}
              disabled={!currentQ || scoring}
              className="flex size-8 items-center justify-center rounded-lg text-[#8A8F98] transition-colors hover:bg-white/[0.06] hover:text-[#EDEDEF] disabled:opacity-40"
              aria-label="화상 답변"
            >
              <Camera className="size-[17px]" />
            </button>
            {hasVoicePending && (
              <span className="flex items-center gap-1 rounded-full border border-[#5E6AD2]/30 bg-[#5E6AD2]/15 px-2 py-0.5 text-[10px] text-[#aab2ef]">
                <Check className="size-2.5" /> 음성 전사됨
              </span>
            )}
            <span className="ml-1 rounded-full border border-white/[0.06] bg-white/[0.05] px-2 py-0.5 text-[10px] text-[#8A8F98]">
              {currentQ ? qLabel : "질문 없음"}
            </span>
            <button
              onClick={() => void send()}
              disabled={!currentQ || scoring || !draft.trim()}
              className="ml-auto flex size-[34px] items-center justify-center rounded-[9px] bg-gradient-to-b from-[#7d88de] to-[#5E6AD2] text-white shadow-[0_0_0_1px_rgba(94,106,210,0.5),0_3px_10px_rgba(94,106,210,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] disabled:opacity-40"
              aria-label="보내기"
            >
              <ArrowUp className="size-[17px]" />
            </button>
          </div>
        </div>
      </div>

      {/* 토스트 */}
      {toastMsg && (
        <div
          className="absolute inset-x-4 z-[65] rounded-xl border border-white/10 bg-[#0a0a0c]/95 px-4 py-3 text-[12.5px] shadow-[0_12px_40px_rgba(0,0,0,0.6)] backdrop-blur-md"
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
          onResult={({ transcript, voiceScore }) => {
            setOverlay(null);
            if (transcript) {
              setDraft(transcript);
              pendingVoiceScore.current = voiceScore;
              setHasVoicePending(true);
              toast(
                voiceScore != null
                  ? `전사 완료 — 전달력 ${voiceScore}점 · 확인 후 전송하세요`
                  : "전사 완료 — 확인 후 전송하세요",
              );
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
          onResult={({ transcript, voiceScore, visualScore }) => {
            setOverlay(null);
            if (transcript) {
              setDraft(transcript);
              pendingVoiceScore.current = voiceScore;
              pendingVisualScore.current = visualScore;
              setHasVoicePending(true);
              toast("분석 완료 — 확인 후 전송하세요");
            } else {
              toast("음성이 인식되지 않았습니다 — 텍스트로 입력해 주세요");
            }
          }}
        />
      )}
    </div>
  );
}
