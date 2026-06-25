import { useRef, useState } from "react";
import { useNavigate } from "react-router";
import {
  Plus, Mic, ArrowUp, Sparkles, Menu, X, SquarePen,
  LayoutDashboard, Briefcase, User, Settings, FileText, AlertCircle,
} from "lucide-react";
import { AutoPrepChatModal } from "@/features/autoprep/components/AutoPrepChatModal";
import { uploadAttachment } from "@/features/autoprep/api/autoPrepApi";
import { ThemeToggle } from "@/app/components/layout/ThemeToggle";
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

// 첨부 파일 — 업로드 진행/완료/실패 상태를 칩으로 보여준다.
interface FileItem { file: File; id?: number; uploading: boolean; error?: boolean; }

export function AppHome() {
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const [req, setReq] = useState<AutoPrepRequest | null>(null);
  const [drawer, setDrawer] = useState(false);
  const [files, setFiles] = useState<FileItem[]>([]);
  const fileRef = useRef<HTMLInputElement>(null);

  // 선택/드롭한 파일을 즉시 업로드(kind=ATTACHMENT)해 fileId 확보. AutoPrepLauncher 와 동일 패턴.
  const addFiles = async (list: FileList | null) => {
    if (!list || list.length === 0) return;
    const items: FileItem[] = Array.from(list).map((file) => ({ file, uploading: true }));
    setFiles((prev) => [...prev, ...items]);
    for (const item of items) {
      try {
        const res = await uploadAttachment(item.file);
        setFiles((prev) => prev.map((f) => (f === item ? { ...f, id: res.id, uploading: false } : f)));
      } catch {
        setFiles((prev) => prev.map((f) => (f === item ? { ...f, uploading: false, error: true } : f)));
      }
    }
  };
  const removeFile = (target: FileItem) => setFiles((prev) => prev.filter((f) => f !== target));

  const run = (text: string) => {
    const t = text.trim();
    const ids = files.filter((f) => f.id != null).map((f) => f.id as number);
    if (!t && ids.length === 0) return;
    setReq({ query: t || undefined, attachmentFileIds: ids.length ? ids : undefined });
    setQ("");
    setFiles([]);
  };

  return (
    <div className="ah">
      <header className="ah-top">
        <button className="ah-menu" onClick={() => setDrawer(true)} aria-label="메뉴">
          <Menu size={20} />
        </button>
        <div className="ah-brand">CareerTuner</div>
        <div className="ah-right">
          <ThemeToggle />
          {/* 크레딧 잔량 = mock. 구독 사용권 잔량 표시·실행 전 차감 미리보기는 E 결제 DB/UX 합의 후. 차감은 E 공통서비스가 사용권 먼저→크레딧 보조로 처리(면접 파트는 호출만). */}
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
        {files.length > 0 && (
          <div className="ah-files">
            {files.map((f, i) => (
              <span key={i} className={`ah-file${f.uploading ? " up" : ""}${f.error ? " err" : ""}`}>
                {f.error ? <AlertCircle size={13} /> : <FileText size={13} />}
                <span className="ah-file-n">{f.file.name}</span>
                <button className="ah-file-x" onClick={() => removeFile(f)} aria-label="첨부 제거"><X size={12} /></button>
              </span>
            ))}
          </div>
        )}
        <div className="ah-inputbar">
          <button className="ah-ic" onClick={() => fileRef.current?.click()} aria-label="파일 첨부"><Plus size={20} /></button>
          <input
            className="ah-input"
            placeholder="네이버 백엔드 신입 통째로 준비해줘"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && run(q)}
          />
          <button className="ah-ic" aria-label="음성"><Mic size={18} /></button>
          <button className="ah-send" onClick={() => run(q)} disabled={!q.trim() && !files.some((f) => f.id != null)} aria-label="보내기">
            <ArrowUp size={18} />
          </button>
        </div>
        <input
          ref={fileRef}
          type="file"
          multiple
          className="ah-fileinput"
          onChange={(e) => {
            void addFiles(e.target.files);
            e.target.value = "";
          }}
        />
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
