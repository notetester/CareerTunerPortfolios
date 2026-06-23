import { useCallback, useRef, useState } from "react";

import { runStream } from "../api/autoPrepApi";
import type { AutoPrepRequest, PrepEvent, PrepPlan, PrepStepResult } from "../types/autoPrep";

export type PartUiStatus = "pending" | "running" | "done" | "skipped" | "failed";

export interface PartState {
  key: string;
  status: PartUiStatus;
  substeps: { name: string; desc: string }[];
  result?: PrepStepResult;
}

export interface AutoPrepRunState {
  running: boolean;
  plan: PrepPlan | null;
  parts: PartState[];
  message: string | null;
  error: string | null;
}

const INITIAL: AutoPrepRunState = {
  running: false,
  plan: null,
  parts: [],
  message: null,
  error: null,
};

/** SSE 실행을 구독해 6파트 진행 상태를 누적하는 훅. */
export function useAutoPrepRun() {
  const [state, setState] = useState<AutoPrepRunState>(INITIAL);
  const abortRef = useRef<AbortController | null>(null);

  const start = useCallback(async (req: AutoPrepRequest) => {
    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;
    setState({ ...INITIAL, running: true });
    try {
      await runStream(req, (e) => setState((prev) => reduce(prev, e)), ac.signal);
    } catch (err) {
      if (!ac.signal.aborted) {
        setState((prev) => ({ ...prev, running: false, error: (err as Error).message }));
      }
    } finally {
      setState((prev) => ({ ...prev, running: false }));
    }
  }, []);

  const cancel = useCallback(() => {
    abortRef.current?.abort();
    setState((prev) => ({ ...prev, running: false }));
  }, []);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    setState(INITIAL);
  }, []);

  return { ...state, start, cancel, reset };
}

function reduce(s: AutoPrepRunState, e: PrepEvent): AutoPrepRunState {
  switch (e.type) {
    case "plan":
      return {
        ...s,
        plan: e.plan,
        parts: e.plan.steps.map((key) => ({ key, status: "pending", substeps: [] })),
      };
    case "part-start":
      return { ...s, parts: s.parts.map((p) => (p.key === e.key ? { ...p, status: "running" } : p)) };
    case "substep":
      return {
        ...s,
        parts: s.parts.map((p) =>
          p.key === e.key ? { ...p, substeps: [...p.substeps, { name: e.name, desc: e.desc }] } : p,
        ),
      };
    case "part-done": {
      const status: PartUiStatus =
        e.result.status === "DONE" ? "done" : e.result.status === "SKIPPED" ? "skipped" : "failed";
      return {
        ...s,
        parts: s.parts.map((p) => (p.key === e.result.key ? { ...p, status, result: e.result } : p)),
      };
    }
    case "done":
      return { ...s, message: e.message, running: false };
    case "error":
      return { ...s, error: e.message, running: false };
    default:
      return s;
  }
}
