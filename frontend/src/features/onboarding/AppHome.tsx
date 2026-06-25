import { useState } from "react";
import { useNavigate } from "react-router";
import { Plus, Mic, ArrowUp, Sparkles } from "lucide-react";
import { AutoPrepChatModal } from "@/features/autoprep/components/AutoPrepChatModal";
import type { AutoPrepRequest } from "@/features/autoprep/types/autoPrep";
import "./apphome.css";

/**
 * 앱 첫 화면(온보딩 완료 후) — 마누스 메인 레이아웃 차용: 상단 크레딧·업그레이드, 중앙 한 줄 질문,
 * 하단 고정 입력창. 입력하면 AI 오케스트레이터 인테이크(AutoPrepChatModal)로 그대로 이어진다.
 * docs/AI_ORCHESTRATOR.md 11.4 "검색창 메인" 참조.
 */
export function AppHome() {
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const [req, setReq] = useState<AutoPrepRequest | null>(null);

  const submit = () => {
    const text = q.trim();
    if (!text) return;
    setReq({ query: text });
    setQ("");
  };

  return (
    <div className="ah">
      <header className="ah-top">
        <div className="ah-brand">CareerTuner</div>
        <div className="ah-right">
          <span className="ah-credit">
            <Sparkles size={13} strokeWidth={2} /> 2,400
          </span>
          <button className="ah-up" onClick={() => navigate("/pricing")}>업그레이드</button>
        </div>
      </header>

      <div className="ah-center">
        <h1 className="ah-q">무엇을 준비해드릴까요?</h1>
      </div>

      <div className="ah-dock">
        <div className="ah-inputbar">
          <button className="ah-ic" aria-label="첨부"><Plus size={20} /></button>
          <input
            className="ah-input"
            placeholder="네이버 백엔드 신입 통째로 준비해줘"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()}
          />
          <button className="ah-ic" aria-label="음성"><Mic size={18} /></button>
          <button className="ah-send" onClick={submit} disabled={!q.trim()} aria-label="보내기">
            <ArrowUp size={18} />
          </button>
        </div>
      </div>

      <AutoPrepChatModal
        open={req !== null}
        initialRequest={req}
        onClose={() => setReq(null)}
        onNavigate={(p) => {
          setReq(null);
          navigate(p);
        }}
      />
    </div>
  );
}
