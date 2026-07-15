import { useEffect, useRef, useState } from "react";
import { ArrowUp, Clock3, Plus, RotateCcw, Sparkles, Trash2, X } from "lucide-react";

import { getJobPostingExtraction, intake, uploadAttachment } from "../api/autoPrepApi";
import { useAutoPrepRun } from "../hooks/useAutoPrepRun";
import { displayCompany, displayJobTitle } from "../lib/caseLabels";
import {
  createPrepSession,
  deletePrepSession,
  getPrepSession,
  listPrepSessions,
  prepRelativeTime,
  updatePrepSession,
  type PrepMsg,
  type PrepSessionRecord,
} from "../lib/prepSessions";
import type { AutoPrepIntakeResponse, AutoPrepRequest, PrepCaseCandidate, PrepModeOption } from "../types/autoPrep";
import { AutoPrepWorkView } from "./AutoPrepWorkView";
import { isAppContext } from "@/platform/capacitor";
import { ApiError } from "@/app/lib/api";
import { discardPendingAutoPrepFiles } from "@/app/lib/pendingAutoPrepFiles";
import "./autoprep-modal.css";

interface ChipData {
  kind: "case" | "mode";
  candidates?: PrepCaseCandidate[];
  modes?: PrepModeOption[];
}
interface Msg {
  role: "me" | "ai";
  text: string;
  chips?: ChipData;
}

interface Props {
  open: boolean;
  initialRequest: AutoPrepRequest | null;
  onClose: () => void;
  onNavigate: (path: string) => void;
}

/** 공고 추출(EXTRACTING) 폴링 주기·상한 — 백엔드 워커가 5초 주기라 2.5초 폴링, ~2분까지 기다린다. */
const EXTRACTION_POLL_MS = 2500;
const MAX_EXTRACTION_POLLS = 48;
const INTAKE_ERROR_FALLBACK = "처리 중 오류가 났어요. 잠시 후 다시 시도해 주세요.";

/** 서버가 의도적으로 보낸 한글 ApiError만 React 텍스트로 노출하고, 그 외 내부 오류는 일반 안내로 숨긴다. */
function intakeErrorMessage(error: unknown): string {
  return error instanceof ApiError && /[가-힣]/.test(error.message)
    ? error.message
    : INTAKE_ERROR_FALLBACK;
}

/**
 * AI 오케스트레이터 창 — 좌측 세션 사이드바 + 우측 대화/실행 스레드.
 * 홈 '준비 시작' 즉시 떠서 멀티턴으로 슬롯(지원건·모드)을 채우고, 같은 창에서 작업 과정을 보여준다.
 * 실행 이력은 localStorage 세션으로 남아 과거 준비를 복원·재실행할 수 있다(Claude 데스크탑식).
 */
export function AutoPrepChatModal({ open, initialRequest, onClose, onNavigate }: Props) {
  const run = useAutoPrepRun();
  const [messages, setMessages] = useState<Msg[]>([]);
  const [slots, setSlots] = useState<AutoPrepRequest>({});
  const slotsRef = useRef<AutoPrepRequest>({});
  slotsRef.current = slots;
  const [phase, setPhase] = useState<"intake" | "running">("intake");
  const [thinking, setThinking] = useState(false);
  const [answered, setAnswered] = useState(false);
  // intake 응답이 유실돼도 같은 fileId와 동일 body로 재전송하도록 정확한 요청을 보존한다.
  const [intakeRetryRequest, setIntakeRetryRequest] = useState<AutoPrepRequest | null>(null);
  const intakeRetryRequestRef = useRef<AutoPrepRequest | null>(null);
  intakeRetryRequestRef.current = intakeRetryRequest;
  const started = useRef(false);
  const activeLifecycleDisposedRef = useRef(false);
  const activeLifecycleVersionRef = useRef(0);
  const currentIntakeRequestRef = useRef<AutoPrepRequest | null>(null);
  // 마지막 실행 요청 — 재시도 = 전체 재실행(부분 재실행 API 없음). 모달 닫으면 함께 초기화.
  const lastRunReqRef = useRef<AutoPrepRequest | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  // 공고 추출 완료를 기다리는 폴링 타이머 + 연속 폴링 횟수(상한 초과 시 중단).
  const pollRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pollCountRef = useRef(0);
  const requestAbortRef = useRef<AbortController | null>(null);
  const generationRef = useRef(0);
  const openRef = useRef(open);
  openRef.current = open;
  const mountCycleRef = useRef(0);
  const mountedRef = useRef(false);
  const unmountCleanupTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearPoll = () => {
    if (pollRef.current) {
      clearTimeout(pollRef.current);
      pollRef.current = null;
    }
    pollCountRef.current = 0;
  };

  /** 닫기·새 준비·재오픈 시 이전 인테이크/상태 조회 응답을 모두 무효화한다. */
  const invalidateIntake = () => {
    generationRef.current += 1;
    requestAbortRef.current?.abort();
    requestAbortRef.current = null;
    clearPoll();
  };

  const isCurrentGeneration = (generation: number) =>
    openRef.current && generationRef.current === generation;

  const requestFileIds = (request: AutoPrepRequest | null | undefined): number[] => [
    ...new Set([
      ...(request?.attachmentFileIds ?? []),
      ...(request?.jobPostingFileIds ?? []),
    ]),
  ];

  /** 명시적 세션 이탈에서만 pending 첨부와 persisted retry를 같은 순간 폐기한다. */
  const cleanupRequestLifecycle = (
    request: AutoPrepRequest | null | undefined,
    sessionId: string | null,
    keepalive = false,
  ) => {
    const fileIds = requestFileIds(request);
    if (fileIds.length > 0) void discardPendingAutoPrepFiles(fileIds, { keepalive });
    if (sessionId) updatePrepSession(sessionId, { lastRequest: null });
  };

  const disposeActiveRequest = (keepalive = false) => {
    if (activeLifecycleDisposedRef.current) return;
    activeLifecycleDisposedRef.current = true;
    activeLifecycleVersionRef.current += 1;
    cleanupRequestLifecycle(
      lastRunReqRef.current ?? currentIntakeRequestRef.current
        ?? intakeRetryRequestRef.current ?? slotsRef.current,
      activeIdRef.current,
      keepalive,
    );
  };

  const activateRequestLifecycle = () => {
    activeLifecycleDisposedRef.current = false;
    activeLifecycleVersionRef.current += 1;
  };

  // ── 세션(이력) ──
  const [records, setRecords] = useState<PrepSessionRecord[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const activeIdRef = useRef<string | null>(null);
  activeIdRef.current = activeId;
  /** 읽기 전용 복원 뷰(과거 세션). null 이면 현재(라이브) 대화를 보여준다. */
  const [restored, setRestored] = useState<PrepSessionRecord | null>(null);
  const [draft, setDraft] = useState("");
  const refreshRecords = () => setRecords(listPrepSessions());

  useEffect(() => {
    if (open && initialRequest && !started.current) {
      started.current = true;
      void begin(initialRequest);
    }
    if (open) refreshRecords();
    if (!open) {
      // 부모 라우트가 모달을 직접 닫는 경로도 refs/state 초기화 전에 pending 파일을 회수한다.
      disposeActiveRequest(true);
      invalidateIntake();
      started.current = false;
      lastRunReqRef.current = null;
      currentIntakeRequestRef.current = null;
      setMessages([]);
      setSlots({});
      setPhase("intake");
      setThinking(false);
      setAnswered(false);
      setIntakeRetryRequest(null);
      setActiveId(null);
      setRestored(null);
      setDraft("");
      run.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, initialRequest]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, thinking, run.parts, restored]);

  // 실제 라우트 unmount만 정리한다. StrictMode의 mount→cleanup→remount 검사는 다음 setup이 예약을 취소한다.
  useEffect(() => {
    const cycle = ++mountCycleRef.current;
    mountedRef.current = true;
    if (unmountCleanupTimerRef.current) {
      clearTimeout(unmountCleanupTimerRef.current);
      unmountCleanupTimerRef.current = null;
    }
    return () => {
      // 실제 unmount의 늦은 upload continuation은 지연 cleanup보다 먼저 이 표식으로 차단한다.
      // StrictMode fake cleanup은 이어지는 setup에서 동기적으로 true로 복구된다.
      mountedRef.current = false;
      unmountCleanupTimerRef.current = setTimeout(() => {
        if (mountCycleRef.current !== cycle) return;
        disposeActiveRequest(true);
        invalidateIntake();
      }, 0);
    };
  }, []);

  // 대화 내용 영속 — 라이브 대화가 바뀔 때마다 활성 세션에 반영.
  useEffect(() => {
    if (!activeId || restored) return;
    updatePrepSession(activeId, { messages: messages as PrepMsg[] });
    refreshRecords();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [messages, activeId]);

  // 실행 스냅샷 영속 — 파트 진행/종료 상태를 세션에 반영.
  useEffect(() => {
    if (!activeId || restored || phase !== "running") return;
    const settledStatus = run.error ? "error" : run.running ? "running" : "done";
    updatePrepSession(activeId, {
      parts: run.parts,
      planSlots: run.plan?.slots ?? null,
      caseId: run.plan?.slots.applicationCaseId ?? slots.applicationCaseId ?? null,
      lastRequest: lastRunReqRef.current,
      status: run.parts.length === 0 && run.running ? "running" : settledStatus,
    });
    refreshRecords();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [run.parts, run.running, run.error, run.plan, activeId, phase]);

  /** 자소서 첨부: 읽지 못한 이전 pending을 최신 파일 하나로 교체하고 재실행(WRITE 가 소비). */
  async function attachCoverLetter(file: File) {
    const req = lastRunReqRef.current;
    if (!req || activeLifecycleDisposedRef.current) return;
    const generation = generationRef.current;
    const sessionId = activeIdRef.current;
    const lifecycleVersion = activeLifecycleVersionRef.current;
    const uploaded = await uploadAttachment(file);
    if (!mountedRef.current
        || !openRef.current
        || generationRef.current !== generation
        || activeIdRef.current !== sessionId
        || activeLifecycleDisposedRef.current
        || activeLifecycleVersionRef.current !== lifecycleVersion) {
      await discardPendingAutoPrepFiles([uploaded.id]).catch(() => undefined);
      return;
    }
    const supersededIds = (req.attachmentFileIds ?? []).filter((fileId) => fileId !== uploaded.id);
    const next: AutoPrepRequest = {
      ...req,
      attachmentFileIds: [uploaded.id],
    };
    lastRunReqRef.current = next;
    setSlots(next);
    if (activeIdRef.current) updatePrepSession(activeIdRef.current, { lastRequest: next });
    if (supersededIds.length > 0) void discardPendingAutoPrepFiles(supersededIds);
    void run.start(next);
  }

  async function begin(req: AutoPrepRequest) {
    if (activeIdRef.current) {
      disposeActiveRequest();
    }
    // 이전 실행 요청이 새 인테이크의 pending 파일보다 우선 선택되어 정리를 가로막지 않게 즉시 비운다.
    lastRunReqRef.current = null;
    currentIntakeRequestRef.current = null;
    invalidateIntake();
    const generation = generationRef.current;
    const label = req.query && req.query.trim() ? req.query : "첨부한 파일로 준비해줘";
    const record = createPrepSession(label, req);
    activateRequestLifecycle();
    activeIdRef.current = record.id;
    setActiveId(record.id);
    setRestored(null);
    setMessages([{ role: "me", text: label }]);
    setSlots(req);
    setPhase("intake");
    setIntakeRetryRequest(null);
    run.reset();
    refreshRecords();
    await step(req, generation);
  }

  async function step(req: AutoPrepRequest, generation = generationRef.current) {
    if (!isCurrentGeneration(generation)) return;
    currentIntakeRequestRef.current = req;
    // 이전에 예약된 추출 폴링이 있으면 이 호출이 대체한다(중복 폴링 방지).
    if (pollRef.current) {
      clearTimeout(pollRef.current);
      pollRef.current = null;
    }
    requestAbortRef.current?.abort();
    const controller = new AbortController();
    requestAbortRef.current = controller;
    setThinking(true);
    setAnswered(false);
    setIntakeRetryRequest(null);
    try {
      const res = await intake(req, controller.signal);
      if (!isCurrentGeneration(generation)) return;
      setThinking(false);
      if (res.ready) {
        pollCountRef.current = 0;
        setMessages((m) => [...m, { role: "ai", text: res.message }]);
        setPhase("running");
        lastRunReqRef.current = req;
        currentIntakeRequestRef.current = null;
        void run.start(req);
      } else if (res.nextAsk === "EXTRACTING") {
        handleExtracting(req, res, generation);
      } else if (res.nextAsk === "CASE") {
        pollCountRef.current = 0;
        setMessages((m) => [...m, { role: "ai", text: res.message, chips: { kind: "case", candidates: res.candidates } }]);
      } else if (res.nextAsk === "MODE") {
        pollCountRef.current = 0;
        setMessages((m) => [...m, { role: "ai", text: res.message, chips: { kind: "mode", modes: res.modes } }]);
      } else {
        pollCountRef.current = 0;
        setMessages((m) => [...m, { role: "ai", text: res.message }]);
      }
    } catch (error) {
      if (controller.signal.aborted) return;
      if (!isCurrentGeneration(generation)) return;
      setThinking(false);
      setIntakeRetryRequest(req);
      clearPoll();
      setMessages((m) => [...m, { role: "ai", text: intakeErrorMessage(error) }]);
      if (activeIdRef.current) updatePrepSession(activeIdRef.current, { status: "error" });
    } finally {
      if (requestAbortRef.current === controller) requestAbortRef.current = null;
    }
  }

  /** 공고 추출 진행중(EXTRACTING) — 생성된 caseId 를 슬롯에 묶고, 상태 GET만 폴링한다. */
  function handleExtracting(req: AutoPrepRequest, res: AutoPrepIntakeResponse, generation: number) {
    const caseId = res.applicationCaseId ?? req.applicationCaseId ?? slots.applicationCaseId ?? null;
    if (!caseId) {
      setMessages((m) => [...m, { role: "ai", text: "생성된 지원 건을 확인할 수 없어요. 다시 시도해 주세요." }]);
      return;
    }
    // 텍스트 공고는 지원 건에 복사됐으므로 pending 원본을 즉시 회수하고, 이후 요청에서는 다시 보내지 않는다.
    if (req.jobPostingFileIds?.length) {
      void discardPendingAutoPrepFiles(req.jobPostingFileIds);
    }
    const nextReq: AutoPrepRequest = { ...req, applicationCaseId: caseId, jobPostingFileIds: undefined };
    currentIntakeRequestRef.current = nextReq;
    setSlots(nextReq);
    if (activeIdRef.current) updatePrepSession(activeIdRef.current, { lastRequest: nextReq });
    // 추출중 안내는 한 번만 — 폴링마다 같은 버블을 쌓지 않는다.
    setMessages((m) =>
      m.length && m[m.length - 1].role === "ai" && m[m.length - 1].text === res.message
        ? m
        : [...m, { role: "ai", text: res.message }],
    );
    pollCountRef.current = 0;
    scheduleExtractionPoll(nextReq, caseId, generation);
  }

  function scheduleExtractionPoll(req: AutoPrepRequest, caseId: number, generation: number) {
    if (!isCurrentGeneration(generation)) return;
    if (pollCountRef.current >= MAX_EXTRACTION_POLLS) {
      clearPoll();
      setMessages((m) => [
        ...m,
        { role: "ai", text: "공고를 읽는 데 시간이 오래 걸려요. 잠시 후 다시 시도하거나 지원 건에서 직접 확인해 주세요." },
      ]);
      return;
    }
    pollRef.current = setTimeout(() => void pollExtraction(req, caseId, generation), EXTRACTION_POLL_MS);
  }

  async function pollExtraction(req: AutoPrepRequest, caseId: number, generation: number) {
    if (!isCurrentGeneration(generation)) return;
    pollRef.current = null;
    pollCountRef.current += 1;
    requestAbortRef.current?.abort();
    const controller = new AbortController();
    requestAbortRef.current = controller;
    try {
      const extraction = await getJobPostingExtraction(caseId, controller.signal);
      if (!isCurrentGeneration(generation)) return;
      if (extraction?.status === "QUEUED" || extraction?.status === "RUNNING") {
        scheduleExtractionPoll(req, caseId, generation);
        return;
      }
      clearPoll();
      await step(req, generation);
    } catch {
      if (controller.signal.aborted) return;
      if (!isCurrentGeneration(generation)) return;
      // 일시적인 상태 조회 실패는 인테이크/생성 POST를 재시도하지 않고 상태 GET만 제한 횟수 내 재시도한다.
      scheduleExtractionPoll(req, caseId, generation);
    } finally {
      if (requestAbortRef.current === controller) requestAbortRef.current = null;
    }
  }

  function pickCase(c: PrepCaseCandidate) {
    if (answered) return;
    setAnswered(true);
    // placeholder 원문은 발화 라벨로 안 내보낸다(F-02) — 슬롯 바인딩은 applicationCaseId 가 권위.
    setMessages((m) => [...m, { role: "me", text: `${displayCompany(c.companyName)} ${displayJobTitle(c.jobTitle)}` }]);
    const next = { ...slots, applicationCaseId: c.id };
    setSlots(next);
    void step(next, generationRef.current);
  }

  function pickMode(o: PrepModeOption) {
    if (answered) return;
    setAnswered(true);
    setMessages((m) => [...m, { role: "me", text: o.label }]);
    const next = { ...slots, mode: o.code };
    setSlots(next);
    void step(next, generationRef.current);
  }

  /** 사이드바 세션 클릭 — 라이브 세션이면 라이브 뷰 복귀, 과거 세션이면 읽기 전용 복원. */
  function openRecord(r: PrepSessionRecord) {
    if (r.id === activeId) {
      setRestored(null);
      return;
    }
    let record = getPrepSession(r.id) ?? r;
    // 과거 세션의 pending fileId는 여기서 회수하고 죽은 ID를 재시도 요청에 싣지 않는다.
    if (requestFileIds(record.lastRequest).length > 0) {
      cleanupRequestLifecycle(record.lastRequest, record.id);
      record = { ...record, lastRequest: null };
      refreshRecords();
    }
    // 실행 중 창이 닫혀 '진행'으로 남은 세션 — 다시 열리는 시점엔 이어질 수 없으므로 미완으로 확정하고,
    // 미결 파트는 settle 시켜 복원 뷰에서 가짜 스피너가 돌지 않게 한다(다시 실행으로 이어가는 흐름).
    if (record.status === "running") {
      const settledParts = record.parts.map((p) =>
        p.status === "running" || p.status === "pending" ? { ...p, status: "failed" as const } : p,
      );
      updatePrepSession(record.id, { status: "error", parts: settledParts });
      record = { ...record, status: "error", parts: settledParts };
      refreshRecords();
    }
    setRestored(record);
  }

  /** 복원 세션 "다시 실행" — 그 세션을 라이브로 승격해 마지막 요청을 재실행. */
  function rerunRecord(r: PrepSessionRecord) {
    if (!r.lastRequest) return;
    if (activeIdRef.current && activeIdRef.current !== r.id) {
      disposeActiveRequest();
    }
    activateRequestLifecycle();
    activeIdRef.current = r.id;
    currentIntakeRequestRef.current = null;
    setActiveId(r.id);
    setRestored(null);
    setMessages(r.messages as Msg[]);
    setSlots(r.lastRequest);
    setPhase("running");
    lastRunReqRef.current = r.lastRequest;
    updatePrepSession(r.id, { status: "running" });
    refreshRecords();
    void run.start(r.lastRequest);
  }

  /** '＋ 새 준비' — 스레드를 비우고 하단 입력바로 시작을 유도. */
  function newPrep() {
    disposeActiveRequest();
    invalidateIntake();
    run.reset();
    lastRunReqRef.current = null;
    currentIntakeRequestRef.current = null;
    setActiveId(null);
    setRestored(null);
    setMessages([]);
    setSlots({});
    setPhase("intake");
    setThinking(false);
    setAnswered(false);
    setIntakeRetryRequest(null);
    activeIdRef.current = null;
  }

  function removeRecord(id: string) {
    if (activeId === id) {
      newPrep();
    } else {
      const record = getPrepSession(id);
      cleanupRequestLifecycle(record?.lastRequest, id);
    }
    deletePrepSession(id);
    refreshRecords();
    if (restored?.id === id) setRestored(null);
  }

  function submitDraft() {
    const q = draft.trim();
    if (!q || thinking || run.running) return;
    setDraft("");
    void begin({ query: q });
  }

  function closeModal() {
    disposeActiveRequest(true);
    lastRunReqRef.current = null;
    currentIntakeRequestRef.current = null;
    setIntakeRetryRequest(null);
    invalidateIntake();
    run.reset();
    onClose();
  }

  if (!open) return null;
  const caseId = run.plan?.slots.applicationCaseId ?? slots.applicationCaseId ?? null;
  const lastIdx = messages.length - 1;
  const viewMsgs: Msg[] = restored ? (restored.messages as Msg[]) : messages;
  const emptyThread = viewMsgs.length === 0 && !restored;

  const fullscreen = isAppContext();
  return (
    <div
      className={
        fullscreen
          ? "ap-overlay fixed inset-0 z-50 flex flex-col bg-background"
          : "ap-overlay fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
      }
      onClick={fullscreen ? undefined : closeModal}
    >
      <div
        className={
          fullscreen
            ? "ap-box flex h-full w-full flex-col overflow-hidden bg-card pt-[env(safe-area-inset-top)]"
            : "ap-box flex h-[min(660px,90vh)] w-full max-w-[980px] overflow-hidden rounded-2xl border border-border bg-card shadow-2xl"
        }
        onClick={(e) => e.stopPropagation()}
      >
        {/* ── 좌측 세션 사이드바 (데스크탑 웹 전용) ── */}
        {!fullscreen && (
          <aside className="hidden w-[218px] flex-none flex-col border-r border-border bg-background/60 md:flex">
            <div className="p-3 pb-2">
              <button
                type="button"
                onClick={newPrep}
                className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-dashed border-primary/40 bg-card py-2 text-xs font-bold text-primary transition hover:border-primary"
              >
                <Plus className="size-3.5" /> 새 준비 시작
              </button>
            </div>
            <div className="px-4 pb-1 pt-2 text-[10px] font-bold tracking-[0.08em] text-muted-foreground">
              준비 기록
            </div>
            <div className="flex-1 overflow-y-auto pb-2">
              {records.length === 0 && (
                <p className="px-4 pt-2 text-[11px] leading-relaxed text-muted-foreground">
                  아직 기록이 없어요. 준비를 시작하면 여기 쌓입니다.
                </p>
              )}
              {records.map((r) => {
                const active = restored ? restored.id === r.id : activeId === r.id;
                return (
                  <div
                    key={r.id}
                    className={`group mx-2 mb-0.5 flex items-start gap-1 rounded-lg px-2 py-1.5 transition ${
                      active ? "bg-primary/10" : "hover:bg-secondary"
                    }`}
                  >
                    <button type="button" onClick={() => openRecord(r)} className="min-w-0 flex-1 text-left">
                      <div className="truncate text-[12px] font-semibold text-foreground">{r.title}</div>
                      <div className="mt-0.5 flex items-center gap-1.5 text-[10.5px] text-muted-foreground">
                        <StatusDot status={r.status} />
                        <Clock3 className="size-3" /> {prepRelativeTime(r.updatedAt)}
                      </div>
                    </button>
                    <button
                      type="button"
                      onClick={() => removeRecord(r.id)}
                      aria-label="기록 삭제"
                      className="mt-0.5 hidden text-muted-foreground hover:text-destructive group-hover:block"
                    >
                      <Trash2 className="size-3.5" />
                    </button>
                  </div>
                );
              })}
            </div>
          </aside>
        )}

        {/* ── 우측 스레드 ── */}
        <div className="flex min-w-0 flex-1 flex-col">
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <Sparkles className="size-4 text-primary" aria-hidden /> AI 오케스트레이터
              {restored && (
                <span className="rounded-full bg-secondary px-2 py-0.5 text-[10.5px] font-bold text-muted-foreground">
                  지난 준비 · {prepRelativeTime(restored.updatedAt)}
                </span>
              )}
            </div>
            <button onClick={closeModal} className="text-lg text-muted-foreground transition-colors hover:text-foreground" aria-label="닫기">
              <X className="size-5" />
            </button>
          </div>

          <div ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto bg-background/40 p-4">
            {emptyThread && (
              <div className="flex h-full flex-col items-center justify-center gap-2 text-center">
                <Sparkles className="size-7 text-primary/60" />
                <p className="text-sm font-semibold text-foreground">무엇을 준비할까요?</p>
                <p className="max-w-[320px] text-xs leading-relaxed text-muted-foreground">
                  아래에 회사·직무를 입력하면 6개 AI가 병렬로 준비를 시작해요. 지난 준비는 왼쪽 기록에서 다시 볼 수 있어요.
                </p>
              </div>
            )}

            {viewMsgs.map((msg, i) => (
              <div key={i} className={`flex gap-2 ${msg.role === "me" ? "justify-end" : ""}`}>
                {msg.role === "ai" && (
                  <div className="grid h-7 w-7 flex-none place-items-center rounded-full bg-primary/10 text-primary"><Sparkles className="size-3.5" /></div>
                )}
                <div
                  className={`max-w-[80%] rounded-2xl px-3 py-2 text-[13.5px] leading-relaxed ${
                    msg.role === "me"
                      ? "rounded-br-sm bg-primary text-primary-foreground"
                      : "rounded-bl-sm border border-border bg-card text-foreground"
                  }`}
                >
                  <div>{msg.text}</div>
                  {msg.chips?.kind === "case" && (
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {(msg.chips.candidates ?? []).slice(0, 6).map((c) => (
                        <button
                          key={c.id}
                          onClick={() => pickCase(c)}
                          disabled={Boolean(restored) || answered || i !== lastIdx}
                          className="rounded-lg border border-border bg-card px-2.5 py-1.5 text-left text-xs transition hover:border-primary disabled:opacity-50"
                        >
                          <div className="font-bold text-foreground">{displayCompany(c.companyName)}</div>
                          <div className="text-muted-foreground">{displayJobTitle(c.jobTitle)}</div>
                        </button>
                      ))}
                    </div>
                  )}
                  {msg.chips?.kind === "mode" && (
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {(msg.chips.modes ?? []).map((o) => (
                        <button
                          key={o.code}
                          onClick={() => pickMode(o)}
                          disabled={Boolean(restored) || answered || i !== lastIdx}
                          className="rounded-full border border-border bg-card px-3 py-1 text-xs transition hover:border-primary disabled:opacity-50"
                        >
                          {o.label}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}

            {!restored && thinking && (
              <div className="flex gap-2">
                <div className="grid h-7 w-7 flex-none place-items-center rounded-full bg-primary/10 text-primary"><Sparkles className="size-3.5" /></div>
                <div className="rounded-2xl rounded-bl-sm border border-border bg-card px-4 py-3">
                  <Dots />
                </div>
              </div>
            )}

            {!restored && phase === "intake" && !thinking && intakeRetryRequest && (
              <div className="flex justify-start pl-9">
                <button
                  type="button"
                  onClick={() => void step(intakeRetryRequest, generationRef.current)}
                  className="inline-flex items-center gap-1.5 rounded-full border border-primary/30 bg-card px-3 py-1.5 text-xs font-bold text-primary transition hover:border-primary"
                >
                  <RotateCcw className="size-3.5" /> 같은 요청으로 다시 시도
                </button>
              </div>
            )}

            {!restored && phase === "running" && (
              <AutoPrepWorkView
                running={run.running}
                parts={run.parts}
                caseId={caseId}
                onRetry={() => { if (lastRunReqRef.current) void run.start(lastRunReqRef.current); }}
                onAttachCoverLetter={attachCoverLetter}
                onNavigate={(p) => {
                  closeModal();
                  onNavigate(p);
                }}
              />
            )}

            {restored && restored.parts.length > 0 && (
              <AutoPrepWorkView
                running={false}
                parts={restored.parts}
                caseId={restored.caseId}
                onRetry={restored.lastRequest ? () => rerunRecord(restored) : undefined}
                onNavigate={(p) => {
                  closeModal();
                  onNavigate(p);
                }}
              />
            )}
          </div>

          {/* ── 하단 입력바 — 새 준비를 이 창에서 바로 시작 (데스크탑 웹 전용) ── */}
          {!fullscreen && (
            <div className="border-t border-border bg-card px-3 py-2.5">
              <div className="flex items-center gap-2">
                <input
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") submitDraft();
                  }}
                  placeholder={run.running ? "실행이 끝나면 새 준비를 시작할 수 있어요" : "예: 네이버 백엔드 신입 통째로 준비해줘"}
                  disabled={thinking || run.running}
                  className="min-w-0 flex-1 rounded-lg border border-border bg-background px-3 py-2 text-[13px] text-foreground outline-none placeholder:text-muted-foreground focus:border-primary disabled:opacity-60"
                />
                <button
                  type="button"
                  onClick={submitDraft}
                  disabled={!draft.trim() || thinking || run.running}
                  aria-label="새 준비 시작"
                  className="grid h-9 w-9 flex-none place-items-center rounded-lg bg-primary text-primary-foreground transition hover:brightness-110 disabled:opacity-40"
                >
                  <ArrowUp className="size-4" />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function StatusDot({ status }: { status: PrepSessionRecord["status"] }) {
  const color = status === "done" ? "#059669" : status === "error" ? "#d97706" : "#5e6ad2";
  const label = status === "done" ? "완료" : status === "error" ? "미완" : "진행";
  return (
    <span className="inline-flex items-center gap-1">
      <i className="inline-block h-1.5 w-1.5 rounded-full" style={{ background: color }} aria-hidden />
      <span className="font-semibold">{label}</span>
    </span>
  );
}

function Dots() {
  return (
    <span className="flex gap-1">
      <i className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted-foreground" />
      <i className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted-foreground [animation-delay:.15s]" />
      <i className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted-foreground [animation-delay:.3s]" />
    </span>
  );
}
