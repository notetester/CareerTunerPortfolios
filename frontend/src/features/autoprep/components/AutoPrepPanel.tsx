import { useState } from "react";
import { useNavigate } from "react-router";

import type { AutoPrepRequest } from "../types/autoPrep";
import { AutoPrepChatModal } from "./AutoPrepChatModal";
import { AutoPrepLauncher } from "./AutoPrepLauncher";

/** AI 오케스트레이터 임베드 — 입력하면 즉시 채팅 팝업을 띄우고, 그 안에서 멀티턴 인테이크 → 작업 과정까지 이어간다. */
export function AutoPrepPanel() {
  const navigate = useNavigate();
  const [chatReq, setChatReq] = useState<AutoPrepRequest | null>(null);

  return (
    <>
      <AutoPrepLauncher onRun={(req) => setChatReq(req)} busy={chatReq !== null} />
      <AutoPrepChatModal
        open={chatReq !== null}
        initialRequest={chatReq}
        onClose={() => setChatReq(null)}
        onNavigate={(path) => {
          setChatReq(null);
          navigate(path);
        }}
      />
    </>
  );
}
