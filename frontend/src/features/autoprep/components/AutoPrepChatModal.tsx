import { useEffect, useRef, useState } from "react";

import { intake } from "../api/autoPrepApi";
import { useAutoPrepRun } from "../hooks/useAutoPrepRun";
import { displayCompany, displayJobTitle } from "../lib/caseLabels";
import type { AutoPrepRequest, PrepCaseCandidate, PrepModeOption } from "../types/autoPrep";
import { AutoPrepWorkView } from "./AutoPrepWorkView";
import { isAppContext } from "@/platform/capacitor";
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

/** 채팅 팝업 — 입력 즉시 떠서 멀티턴으로 슬롯(지원건·모드)을 채우고, 준비되면 같은 창에서 작업 과정을 보여준다. */
export function AutoPrepChatModal({ open, initialRequest, onClose, onNavigate }: Props) {
  const run = useAutoPrepRun();
  const [messages, setMessages] = useState<Msg[]>([]);
  const [slots, setSlots] = useState<AutoPrepRequest>({});
  const [phase, setPhase] = useState<"intake" | "running">("intake");
  const [thinking, setThinking] = useState(false);
  const [answered, setAnswered] = useState(false);
  const started = useRef(false);
  // 마지막 실행 요청 — 재시도 = 전체 재실행(부분 재실행 API 없음). 모달 닫으면 함께 초기화.
  const lastRunReqRef = useRef<AutoPrepRequest | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open && initialRequest && !started.current) {
      started.current = true;
      void begin(initialRequest);
    }
    if (!open) {
      started.current = false;
      lastRunReqRef.current = null;
      setMessages([]);
      setSlots({});
      setPhase("intake");
      setThinking(false);
      setAnswered(false);
      run.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, initialRequest]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, thinking, run.parts]);

  async function begin(req: AutoPrepRequest) {
    const label = req.query && req.query.trim() ? req.query : "첨부한 파일로 준비해줘";
    setMessages([{ role: "me", text: label }]);
    setSlots(req);
    await step(req);
  }

  async function step(req: AutoPrepRequest) {
    setThinking(true);
    setAnswered(false);
    try {
      const res = await intake(req);
      setThinking(false);
      if (res.ready) {
        setMessages((m) => [...m, { role: "ai", text: res.message }]);
        setPhase("running");
        lastRunReqRef.current = req;
        void run.start(req);
      } else if (res.nextAsk === "CASE") {
        setMessages((m) => [...m, { role: "ai", text: res.message, chips: { kind: "case", candidates: res.candidates } }]);
      } else if (res.nextAsk === "MODE") {
        setMessages((m) => [...m, { role: "ai", text: res.message, chips: { kind: "mode", modes: res.modes } }]);
      } else {
        setMessages((m) => [...m, { role: "ai", text: res.message }]);
      }
    } catch {
      setThinking(false);
      setMessages((m) => [...m, { role: "ai", text: "처리 중 오류가 났어요. 잠시 후 다시 시도해 주세요." }]);
    }
  }

  function pickCase(c: PrepCaseCandidate) {
    if (answered) return;
    setAnswered(true);
    // placeholder 원문은 발화 라벨로 안 내보낸다(F-02) — 슬롯 바인딩은 applicationCaseId 가 권위.
    setMessages((m) => [...m, { role: "me", text: `${displayCompany(c.companyName)} ${displayJobTitle(c.jobTitle)}` }]);
    const next = { ...slots, applicationCaseId: c.id };
    setSlots(next);
    void step(next);
  }

  function pickMode(o: PrepModeOption) {
    if (answered) return;
    setAnswered(true);
    setMessages((m) => [...m, { role: "me", text: o.label }]);
    const next = { ...slots, mode: o.code };
    setSlots(next);
    void step(next);
  }

  if (!open) return null;
  const caseId = run.plan?.slots.applicationCaseId ?? slots.applicationCaseId ?? null;
  const lastIdx = messages.length - 1;

  const fullscreen = isAppContext();
  return (
    <div
      className={
        fullscreen
          ? "ap-overlay fixed inset-0 z-50 flex flex-col bg-background"
          : "ap-overlay fixed inset-0 z-50 flex items-start justify-center bg-black/50 p-4 pt-[6vh] backdrop-blur-sm"
      }
      onClick={fullscreen ? undefined : onClose}
    >
      <div
        className={
          fullscreen
            ? "ap-box flex h-full w-full flex-col overflow-hidden bg-card pt-[env(safe-area-inset-top)]"
            : "ap-box flex h-[min(680px,86vh)] w-full max-w-[560px] flex-col overflow-hidden rounded-2xl border border-border bg-card shadow-2xl"
        }
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <span aria-hidden>✦</span> AI 오케스트레이터
          </div>
          <button onClick={onClose} className="text-lg text-muted-foreground transition-colors hover:text-foreground" aria-label="닫기">
            ✕
          </button>
        </div>

        <div ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto bg-background/40 p-4">
          {messages.map((msg, i) => (
            <div key={i} className={`flex gap-2 ${msg.role === "me" ? "justify-end" : ""}`}>
              {msg.role === "ai" && (
                <div className="grid h-7 w-7 flex-none place-items-center rounded-full bg-primary/10 text-sm">✦</div>
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
                        disabled={answered || i !== lastIdx}
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
                        disabled={answered || i !== lastIdx}
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

          {thinking && (
            <div className="flex gap-2">
              <div className="grid h-7 w-7 flex-none place-items-center rounded-full bg-primary/10 text-sm">✦</div>
              <div className="rounded-2xl rounded-bl-sm border border-border bg-card px-4 py-3">
                <Dots />
              </div>
            </div>
          )}

          {phase === "running" && (
            <AutoPrepWorkView
              running={run.running}
              parts={run.parts}
              caseId={caseId}
              onRetry={() => { if (lastRunReqRef.current) void run.start(lastRunReqRef.current); }}
              onNavigate={(p) => {
                onClose();
                onNavigate(p);
              }}
            />
          )}
        </div>
      </div>
    </div>
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
