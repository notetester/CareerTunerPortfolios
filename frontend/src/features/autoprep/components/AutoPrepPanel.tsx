import { useState } from "react";
import { useNavigate } from "react-router";

import { useAutoPrepRun } from "../hooks/useAutoPrepRun";
import type { AutoPrepRequest } from "../types/autoPrep";
import { AutoPrepLauncher } from "./AutoPrepLauncher";
import { AutoPrepModal } from "./AutoPrepModal";

/** AI 오케스트레이터 임베드 단위 — 런처(입력) + 작업 과정 팝업(SSE)을 묶는다. */
export function AutoPrepPanel() {
  const run = useAutoPrepRun();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const handleRun = (req: AutoPrepRequest) => {
    setOpen(true);
    void run.start(req);
  };

  return (
    <>
      <AutoPrepLauncher onRun={handleRun} busy={run.running} />
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
