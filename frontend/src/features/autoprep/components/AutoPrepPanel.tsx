import { useState } from "react";
import { useNavigate } from "react-router";

import { intake } from "../api/autoPrepApi";
import { useAutoPrepRun } from "../hooks/useAutoPrepRun";
import type { AutoPrepRequest, PrepCaseCandidate } from "../types/autoPrep";
import { AutoPrepLauncher } from "./AutoPrepLauncher";
import { AutoPrepModal } from "./AutoPrepModal";

/**
 * AI 오케스트레이터 임베드 단위 — 런처(입력) → 인테이크(슬롯 확인) → 작업 과정 팝업(SSE).
 * 인테이크가 지원 건을 못 찾으면 후보를 띄워 고르게 하고, 고른 caseId 로 실행한다.
 */
export function AutoPrepPanel() {
  const run = useAutoPrepRun();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [intaking, setIntaking] = useState(false);
  const [pendingReq, setPendingReq] = useState<AutoPrepRequest | null>(null);
  const [candidates, setCandidates] = useState<PrepCaseCandidate[]>([]);
  const [intakeMessage, setIntakeMessage] = useState<string | null>(null);

  const startRun = (req: AutoPrepRequest) => {
    setCandidates([]);
    setIntakeMessage(null);
    setPendingReq(null);
    setOpen(true);
    void run.start(req);
  };

  const handleRun = async (req: AutoPrepRequest) => {
    setIntaking(true);
    setCandidates([]);
    setIntakeMessage(null);
    try {
      const res = await intake(req);
      if (res.ready) {
        startRun(req);
      } else {
        // 지원 건이 필요한데 못 찾음 → 후보를 띄워 고르게 한다.
        setPendingReq(req);
        setCandidates(res.candidates ?? []);
        setIntakeMessage(res.message);
      }
    } catch {
      // 인테이크 실패는 치명적이지 않다 — 그대로 실행(오케가 단계별로 처리).
      startRun(req);
    } finally {
      setIntaking(false);
    }
  };

  const handlePickCase = (caseId: number) => {
    startRun({ ...(pendingReq ?? {}), applicationCaseId: caseId });
  };

  return (
    <>
      <AutoPrepLauncher
        onRun={handleRun}
        busy={run.running || intaking}
        intakeMessage={intakeMessage}
        candidates={candidates}
        onPickCase={handlePickCase}
      />
      <AutoPrepModal
        open={open}
        onClose={() => setOpen(false)}
        running={run.running}
        plan={run.plan}
        parts={run.parts}
        message={run.message}
        error={run.error}
        onNavigate={(path) => {
          setOpen(false);
          navigate(path);
        }}
      />
    </>
  );
}
