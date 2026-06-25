import { useState } from "react";
import { useNavigate } from "react-router";
import {
  Plus, Mic, ArrowUp, Sparkles, Menu, X, SquarePen,
  LayoutDashboard, Briefcase, User, Settings,
} from "lucide-react";
import { AutoPrepChatModal } from "@/features/autoprep/components/AutoPrepChatModal";
import type { AutoPrepRequest } from "@/features/autoprep/types/autoPrep";
import "./apphome.css";

/**
 * 앱 첫 화면(온보딩 완료 후) — 마누스 메인 레이아웃 차용.
 * 상단 메뉴(드로어)·크레딧, 중앙 한 줄 질문 + 추천 칩, 하단 입력창.
 * 입력/칩 → AI 오케스트레이터 인테이크(AutoPrepChatModal)로 이어진다.
 * docs/AI_ORCHESTRATOR.md 11.4 "검색창 메인" 참조.
 */

// 질문을 뭐라 해야 할지 모를 때 참고용 추천 칩(랜딩 hero 와 동일). 누르면 즉시 실행된다.
const CHIPS = ["자소서부터 봐줘", "압박 면접 연습하고 싶어", "카카오 프론트엔드 면접 준비해줘"];

// 좌측 드로어 메뉴 — 실제 라우트로 이동.
const MENU = [
  { label: "대시보드", to: "/dashboard", Icon: LayoutDashboard },
  { label: "지원 건 관리", to: "/applications", Icon: Briefcase },
  { label: "내 프로필", to: "/profile", Icon: User },
  { label: "요금제", to: "/pricing", Icon: Sparkles },
  { label: "설정", to: "/settings", Icon: Settings },
];

// 최근 준비 세션 — 목업. 실데이터는 autoprep 세션 저장 구현 후 이 자리에 연결.
const RECENT = [
  { id: 1, title: "네이버 백엔드 신입 통째로 준비", when: "오늘" },
  { id: 2, title: "카카오 프론트엔드 면접", when: "어제" },
  { id: 3, title: "토스 서버 직무 면접", when: "3일 전" },
];

export function AppHome() {
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const [req, setReq] = useState<AutoPrepRequest | null>(null);
  const [drawer, setDrawer] = useState(false);

  const run = (text: string) => {
    const t = text.trim();
    if (!t) return;
    setReq({ query: t });
    setQ("");
  };

  return (
    <div className="ah">
      <header className="ah-top">
        <button className="ah-menu" onClick={() => setDrawer(true)} aria-label="메뉴">
          <Menu size={20} />
        </button>
        <div className="ah-brand">CareerTuner</div>
        <div className="ah-right">
          <span className="ah-credit"><Sparkles size={13} strokeWidth={2} /> 2,400</span>
          <button className="ah-up" onClick={() => navigate("/pricing")}>업그레이드</button>
        </div>
      </header>

      <div className="ah-center">
        <h1 className="ah-q">무엇을 준비해드릴까요?</h1>
      </div>

      <div className="ah-dock">
        <div className="ah-chips">
          {CHIPS.map((c) => (
            <button key={c} className="ah-chip" onClick={() => run(c)}>{c}</button>
          ))}
        </div>
        <div className="ah-inputbar">
          <button className="ah-ic" aria-label="첨부"><Plus size={20} /></button>
          <input
            className="ah-input"
            placeholder="네이버 백엔드 신입 통째로 준비해줘"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && run(q)}
          />
          <button className="ah-ic" aria-label="음성"><Mic size={18} /></button>
          <button className="ah-send" onClick={() => run(q)} disabled={!q.trim()} aria-label="보내기">
            <ArrowUp size={18} />
          </button>
        </div>
      </div>

      {drawer && (
        <div className="ah-drawer-wrap" onClick={() => setDrawer(false)}>
          <aside className="ah-drawer" onClick={(e) => e.stopPropagation()}>
            <div className="ah-dr-top">
              <span className="ah-dr-title">CareerTuner</span>
              <button className="ah-dr-x" onClick={() => setDrawer(false)} aria-label="닫기"><X size={18} /></button>
            </div>
            <button className="ah-dr-new" onClick={() => setDrawer(false)}>
              <SquarePen size={16} /> 새 준비 시작
            </button>
            <div className="ah-dr-sect">최근 준비 세션</div>
            <div className="ah-dr-list">
              {RECENT.map((s) => (
                <button key={s.id} className="ah-dr-item" onClick={() => setDrawer(false)}>
                  <span className="ah-dr-t">{s.title}</span>
                  <span className="ah-dr-when">{s.when}</span>
                </button>
              ))}
            </div>
            <nav className="ah-dr-menu">
              {MENU.map(({ label, to, Icon }) => (
                <button key={to} className="ah-dr-link" onClick={() => { setDrawer(false); navigate(to); }}>
                  <Icon size={17} /> {label}
                </button>
              ))}
            </nav>
          </aside>
        </div>
      )}

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
