import { useState } from "react";
import { useNavigate } from "react-router";

import type { AutoPrepRequest } from "../types/autoPrep";
import { AutoPrepChatModal } from "./AutoPrepChatModal";
import { AutoPrepLauncher } from "./AutoPrepLauncher";

/** AI 오케스트레이터 임베드 — 입력하면 즉시 창을 띄우고, 그 안에서 멀티턴 인테이크 → 작업 과정까지 이어간다.
 *  '기록'은 요청 없이 창만 열어 지난 준비 세션을 복원해 본다. */
export function AutoPrepPanel() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [chatReq, setChatReq] = useState<AutoPrepRequest | null>(null);

  return (
    <>
      <AutoPrepLauncher
        onRun={(req) => {
          setChatReq(req);
          setOpen(true);
        }}
        onHistory={() => {
          setChatReq(null);
          setOpen(true);
        }}
        busy={open}
      />
      <AutoPrepChatModal
        open={open}
        initialRequest={chatReq}
        onClose={() => {
          setOpen(false);
          setChatReq(null);
        }}
        onNavigate={(path) => {
          setOpen(false);
          setChatReq(null);
          navigate(path);
        }}
      />
    </>
  );
}
