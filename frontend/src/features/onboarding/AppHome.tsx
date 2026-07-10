import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router";
import {
  Plus, Mic, ArrowUp, Sparkles, Menu, X, SquarePen, Camera,
  LayoutDashboard, Briefcase, User, Settings, FileText, AlertCircle,
  ChevronLeft, MessageSquare,
} from "lucide-react";
import { uploadAttachment } from "@/features/autoprep/api/autoPrepApi";
import { AutoPrepWorkView } from "@/features/autoprep/components/AutoPrepWorkView";
import { displayCompany, displayJobTitle } from "@/features/autoprep/lib/caseLabels";
import { useAuth } from "@/app/auth/AuthContext";
import { useApplicationCases } from "@/features/applications/hooks/useApplicationCases";
import { listInterviewSessions } from "@/features/interview/api/interviewApi";
import { getInterviewModeLabel } from "@/features/interview/types/interview";
import type { InterviewMode, InterviewSession } from "@/features/interview/types/interview";
import { useChatbot } from "@/features/support/hooks/useChatbot";
import { capturePhotoFile } from "@/platform/nativeCamera";
import "./apphome.css";

/**
 * 앱 첫 화면(온보딩 완료 후) — Linear Modern 다크 고정.
 * 상단 메뉴(드로어)·크레딧, 중앙 그라데이션 헤드라인 + 최근 대화/세션, 하단 입력 독.
 *
 * 입력은 통합 챗봇(useChatbot → POST /chatbot/ask)으로 흐른다 — 홈 화면 자체가 대화 스레드로
 * 전환되고(팝업 없음), 대화는 conversationId 단위로 서버에 저장돼 목록/복원이 된다.
 * 오케스트레이터 인테이크(지원건·모드 칩)와 6파트 실행(AutoPrepWorkView)도 같은 스레드 안에서 진행.
 * docs/mobile-app-v2-mockup.html 홈 화면과 동일 톤. docs/AI_ORCHESTRATOR.md 11.4 참조.
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

// 첨부 파일 — 업로드 진행/완료/실패 상태를 칩으로 보여준다.
interface FileItem { file: File; id?: number; uploading: boolean; error?: boolean; }

/** 상대 시각 라벨 (MobileSessionsPage 와 동일 규칙). */
function relTime(iso: string | null | undefined): string {
  if (!iso) return "";
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return "방금";
  if (min < 60) return `${min}분 전`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}시간 전`;
  const day = Math.floor(hr / 24);
  if (day < 7) return `${day}일 전`;
  return new Date(iso).toLocaleDateString("ko-KR", { month: "short", day: "numeric" });
}

export function AppHome() {
  const navigate = useNavigate();
  const { isAuthenticated, loading: authLoading } = useAuth();
  const cases = useApplicationCases(isAuthenticated);
  const chat = useChatbot();
  const [q, setQ] = useState("");
  // 홈(헤드라인+최근 목록) ↔ 채팅(스레드) 뷰. 스레드 상태는 훅에 남아 있어 세션 목록에서 언제든 복귀.
  const [view, setView] = useState<"home" | "chat">("home");
  const [drawer, setDrawer] = useState(false);
  const [files, setFiles] = useState<FileItem[]>([]);
  const [sessions, setSessions] = useState<InterviewSession[]>([]);
  const fileRef = useRef<HTMLInputElement>(null);
  const captureRef = useRef<HTMLInputElement>(null);
  const threadRef = useRef<HTMLDivElement>(null);
  // 이번 대화에서 올린 첨부 누적 — ready 시 run 요청에 병합(setPendingAttachments 는 교체 방식이라 합집합 유지).
  const sentAttachmentIdsRef = useRef<number[]>([]);

  // 최근 면접 세션 — 로그인 상태에서만 로드. 실패하면 빈 배열 → 섹션 숨김.
  useEffect(() => {
    if (authLoading || !isAuthenticated) return;
    let alive = true;
    void (async () => {
      try {
        const page = await listInterviewSessions(0, 5);
        if (alive) setSessions(page.sessions);
      } catch {
        if (alive) setSessions([]);
      }
    })();
    return () => { alive = false; };
  }, [authLoading, isAuthenticated]);

  // 저장된 대화 세션 목록(서버 conversationId 단위) — 홈 목록/드로어에 노출.
  useEffect(() => {
    if (authLoading || !isAuthenticated) return;
    chat.loadSessions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, isAuthenticated]);

  // 스레드 자동 스크롤 — 메시지/실행 진행/생각중 변화마다 바닥으로.
  useEffect(() => {
    threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight, behavior: "smooth" });
  }, [chat.messages, chat.botStatus, chat.runParts]);

  // 세션 라벨 = "회사명 · 직무" (지원 건 매핑).
  const caseLabel = useMemo(() => {
    const map = new Map(cases.applicationCases.map((c) => [c.id, `${c.companyName} · ${c.jobTitle}`]));
    return (id: number) => map.get(id) ?? `지원건 #${id}`;
  }, [cases.applicationCases]);

  const openInterviewSession = (id: number) => {
    setDrawer(false);
    navigate(`/m/session/${id}`);
  };

  // 저장된 대화 열기 — 메시지 복원 + 스레드 뷰 전환.
  const openChat = (id: string) => {
    setDrawer(false);
    sentAttachmentIdsRef.current = [];
    chat.openSession(id);
    setView("chat");
  };

  // 새 대화 — 훅 상태 초기화 후 홈으로(홈 입력 = 새 대화 시작).
  const startNewChat = () => {
    setDrawer(false);
    sentAttachmentIdsRef.current = [];
    chat.newSession();
    chat.loadSessions();
    setView("home");
  };

  // 홈 복귀 — 스레드는 훅에 남는다. 목록 갱신해 방금 대화가 최근 목록에 보이게.
  const backHome = () => {
    setView("home");
    chat.loadSessions();
  };

  // 선택/드롭/촬영한 파일을 즉시 업로드(kind=ATTACHMENT)해 fileId 확보. AutoPrepLauncher 와 동일 패턴.
  const addFiles = async (list: FileList | File[] | null) => {
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

  // 공고 찍어서 등록 — 네이티브 카메라(capacitor) 우선, 웹/미지원/취소면 input capture 폴백.
  const captureJobPosting = async () => {
    const file = await capturePhotoFile();
    if (file) void addFiles([file]);
    else captureRef.current?.click();
  };

  // 전송 — 홈에서 보내면 새 대화 시작(이전 스레드가 남아 있으면 비우고), 스레드에선 대화 계속.
  const run = (text: string) => {
    const t = text.trim();
    const ids = files.filter((f) => f.id != null).map((f) => f.id as number);
    if (!t && ids.length === 0) return;
    if (chat.botStatus === "thinking") return;
    if (view === "home" && chat.messages.length > 0) {
      chat.newSession();
      sentAttachmentIdsRef.current = [];
    }
    if (ids.length > 0) {
      sentAttachmentIdsRef.current = [...sentAttachmentIdsRef.current, ...ids];
      chat.setPendingAttachments(sentAttachmentIdsRef.current);
    }
    chat.sendMessage(t || "첨부한 파일로 준비해줘");
    setQ("");
    setFiles([]);
    setView("chat");
  };

  // 면접 인계 — caseId 표식을 남기고 면접 페이지로(위젯 goInterview 와 동일 규칙).
  const goInterview = (caseId: number | null) => {
    chat.markInterviewHandoff(caseId);
    navigate(caseId != null ? `/interview?caseId=${caseId}&tab=modes` : "/interview");
  };
  // WorkView 산출 링크가 면접이면 caseId 를 추출해 표식 후 이동.
  const navigateFromWork = (path: string) => {
    if (path.startsWith("/interview")) {
      const query = path.split("?")[1] ?? "";
      const cid = new URLSearchParams(query).get("caseId");
      chat.markInterviewHandoff(cid ? Number(cid) : null);
    }
    navigate(path);
  };

  const recent = sessions.slice(0, 3);
  const chatSessions = chat.sessions.slice(0, 3);
  const lastBot = [...chat.messages].reverse().find((m) => m.role === "bot");
  const activeTitle =
    chat.sessions.find((s) => s.id === chat.activeSessionId)?.title
    ?? chat.messages.find((m) => m.role === "user")?.text
    ?? "새 대화";

  const dock = (
    <div className="ah-dock">
      {view === "home" && (
        <div className="ah-chips">
          {CHIPS.map((c) => (
            <button key={c} className="ah-chip" onClick={() => run(c)}>{c}</button>
          ))}
          <button className="ah-chip ah-chip-cam" onClick={() => void captureJobPosting()}>
            <Camera size={13} strokeWidth={2} /> 공고 찍어서 등록
          </button>
        </div>
      )}
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
          placeholder={view === "chat" ? "메시지 보내기" : "네이버 백엔드 신입 통째로 준비해줘"}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && run(q)}
        />
        <button className="ah-ic" aria-label="음성"><Mic size={18} /></button>
        <button
          className="ah-send"
          onClick={() => run(q)}
          disabled={chat.botStatus === "thinking" || (!q.trim() && !files.some((f) => f.id != null))}
          aria-label="보내기"
        >
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
      {/* 카메라 폴백 — 웹/플러그인 미지원 시 후면 카메라 캡처 input. */}
      <input
        ref={captureRef}
        type="file"
        accept="image/*"
        capture="environment"
        className="ah-fileinput"
        onChange={(e) => {
          void addFiles(e.target.files);
          e.target.value = "";
        }}
      />
    </div>
  );

  return (
    <div className="ah">
      <div className="ah-blob" aria-hidden="true" />

      {view === "home" ? (
        <header className="ah-top">
          <button className="ah-menu" onClick={() => setDrawer(true)} aria-label="메뉴">
            <Menu size={20} />
          </button>
          <div className="ah-brand">CareerTuner</div>
          <div className="ah-right">
            {/* 크레딧 잔량 = mock. 구독 사용권 잔량 표시·실행 전 차감 미리보기는 E 결제 DB/UX 합의 후. 차감은 E 공통서비스가 사용권 먼저→크레딧 보조로 처리(면접 파트는 호출만). */}
            <span className="ah-credit"><Sparkles size={13} strokeWidth={2} /> 2,400</span>
            <button className="ah-up" onClick={() => navigate("/pricing")}>업그레이드</button>
          </div>
        </header>
      ) : (
        <header className="ah-top">
          <button className="ah-menu" onClick={backHome} aria-label="뒤로">
            <ChevronLeft size={20} />
          </button>
          <div className="ah-brand ah-thread-title">{activeTitle}</div>
          <div className="ah-right">
            <button className="ah-menu" onClick={startNewChat} aria-label="새 대화">
              <SquarePen size={18} />
            </button>
          </div>
        </header>
      )}

      {view === "home" ? (
        <div className="ah-center">
          <h1 className="ah-q">무엇을 준비해드릴까요?</h1>
          {chatSessions.length > 0 && (
            <div className="ah-recent">
              <div className="ah-recent-label">최근 대화</div>
              {chatSessions.map((s) => (
                <button key={s.id} className="ah-sess" onClick={() => openChat(s.id)}>
                  <span className="ah-sess-t">{s.title}</span>
                  <span className="ah-sess-m">
                    <MessageSquare size={11} />
                    {s.mode ? `${getInterviewModeLabel(s.mode as InterviewMode)} · ` : ""}
                    {relTime(s.updatedAt ? new Date(s.updatedAt).toISOString() : null) || "대화"}
                  </span>
                </button>
              ))}
            </div>
          )}
          {recent.length > 0 && (
            <div className="ah-recent">
              <div className="ah-recent-label">최근 면접 세션</div>
              {recent.map((s) => {
                const done = s.endedAt != null;
                return (
                  <button key={s.id} className="ah-sess" onClick={() => openInterviewSession(s.id)}>
                    <span className="ah-sess-t">{caseLabel(s.applicationCaseId)}</span>
                    <span className="ah-sess-m">
                      <span className={`ah-dot ${done ? "done" : "run"}`} />
                      {getInterviewModeLabel(s.mode)}
                      {" · "}
                      {done ? "완료" : "진행 중"}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      ) : (
        <div ref={threadRef} className="ah-thread">
          {chat.messages.map((m) =>
            m.role === "user" ? (
              <div key={m.id} className="ah-b ah-b-me">{m.text}</div>
            ) : (
              <div key={m.id} className="ah-b-wrap">
                <div className="ah-b ah-b-ai">{m.text}</div>
                {m.links.length > 0 && (
                  <div className="ah-b-links">
                    {m.links.map((l) => (
                      <button key={l.url} className="ah-b-link" onClick={() => navigate(l.url)}>{l.label}</button>
                    ))}
                  </div>
                )}
                {/* 마지막 봇 턴의 인테이크 칩 — 지원 건/면접 모드 선택(실행 시작 전까지만). */}
                {m.id === lastBot?.id && m.intake && !m.intake.ready && !chat.runStarted && (
                  <div className="ah-b-links">
                    {m.intake.nextAsk === "CASE" && m.intake.candidates.length === 0 && (
                      <button className="ah-b-link" onClick={() => navigate("/applications/new")}>
                        지원 건 먼저 만들기
                      </button>
                    )}
                    {m.intake.nextAsk === "CASE" &&
                      m.intake.candidates.slice(0, 6).map((c) => (
                        <button key={c.id} className="ah-cand" onClick={() => chat.selectCase(c)}>
                          <b>{displayCompany(c.companyName)}</b>
                          <span>{displayJobTitle(c.jobTitle)}</span>
                        </button>
                      ))}
                    {m.intake.nextAsk === "MODE" &&
                      m.intake.modes.map((o) => (
                        <button key={o.code} className="ah-b-link" onClick={() => chat.selectMode(o)}>
                          {o.label}
                        </button>
                      ))}
                  </div>
                )}
                {/* 인테이크 완료(ready) 턴 — 면접 딥링크 칩(위젯과 동일한 다음 행동 못박기). */}
                {m.id === lastBot?.id && m.intake?.ready && (
                  <div className="ah-b-links">
                    <button className="ah-b-link ah-b-go" onClick={() => goInterview(m.intake?.caseId ?? chat.runCaseId)}>
                      면접 보러 가기
                    </button>
                  </div>
                )}
                {/* 마지막 봇 턴 quickReply — 다음 질문 후보. */}
                {m.id === lastBot?.id && !chat.runStarted && m.quickReplies.length > 0 && (
                  <div className="ah-b-links">
                    {m.quickReplies.map((r) => (
                      <button key={r} className="ah-b-link" onClick={() => run(r)}>{r}</button>
                    ))}
                  </div>
                )}
              </div>
            ),
          )}

          {/* 오케스트레이터 6파트 실행 — 같은 스레드 안에서 진행(팝업 없음). */}
          {chat.runStarted && (
            <div className="ah-runwrap">
              <AutoPrepWorkView
                running={chat.runRunning}
                parts={chat.runParts}
                caseId={chat.runCaseId}
                company={chat.runPlan?.slots.company ?? null}
                onRetry={chat.retryRun}
                onAttachCoverLetter={chat.attachCoverLetter}
                onNavigate={navigateFromWork}
              />
              {chat.runError && !chat.runRunning && (
                <div className="ah-run-err">
                  <AlertCircle size={13} /> {chat.runError}
                  {chat.runParts.length === 0 && (
                    <button className="ah-b-link" onClick={chat.retryRun}>다시 시도</button>
                  )}
                </div>
              )}
            </div>
          )}

          {chat.botStatus === "thinking" && (
            <div className="ah-b ah-b-ai ah-typing" aria-label="응답 생성 중">
              <i /><i /><i />
            </div>
          )}
          {chat.botStatus === "disconnected" && (
            <div className="ah-run-err">
              <AlertCircle size={13} /> 연결이 불안정해요. 잠시 후 다시 보내주세요.
            </div>
          )}
        </div>
      )}

      {dock}

      {drawer && (
        <div className="ah-drawer-wrap" onClick={() => setDrawer(false)}>
          <aside className="ah-drawer" onClick={(e) => e.stopPropagation()}>
            <div className="ah-dr-top">
              <span className="ah-dr-title">CareerTuner</span>
              <button className="ah-dr-x" onClick={() => setDrawer(false)} aria-label="닫기"><X size={18} /></button>
            </div>
            <button className="ah-dr-new" onClick={startNewChat}>
              <SquarePen size={16} /> 새 준비 시작
            </button>
            {chat.sessions.length > 0 && (
              <>
                <div className="ah-dr-sect">대화</div>
                <div className="ah-dr-list">
                  {chat.sessions.map((s) => (
                    <button key={s.id} className="ah-dr-item" onClick={() => openChat(s.id)}>
                      <span className="ah-dr-t">{s.title}</span>
                      <span className="ah-dr-when">
                        {relTime(s.updatedAt ? new Date(s.updatedAt).toISOString() : null) || "대화"}
                      </span>
                    </button>
                  ))}
                </div>
              </>
            )}
            {sessions.length > 0 && (
              <>
                <div className="ah-dr-sect">최근 면접 세션</div>
                <div className="ah-dr-list">
                  {sessions.map((s) => (
                    <button key={s.id} className="ah-dr-item" onClick={() => openInterviewSession(s.id)}>
                      <span className="ah-dr-t">{caseLabel(s.applicationCaseId)}</span>
                      <span className="ah-dr-when">
                        {getInterviewModeLabel(s.mode)} · {relTime(s.lastResumedAt ?? s.createdAt)}
                      </span>
                    </button>
                  ))}
                </div>
              </>
            )}
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
    </div>
  );
}
