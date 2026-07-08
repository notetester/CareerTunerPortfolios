import { useEffect, useState, useRef } from "react";
import { Link } from "react-router";
import {
  ArrowLeft, Send, UploadCloud, X, Paperclip,
  Clock, CalendarDays, Mail, MessageCircle, Check,
} from "lucide-react";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/app/components/ui/select";
import { getAccessToken } from "@/app/lib/tokenStore";
import { apiBase } from "@/app/lib/apiBase";
import { CONTACT_CATEGORIES, type SupportTicket, type TicketAttachment, type TicketStatus, type TicketThread } from "../types/support";
import { addTicketMessage, getTicketThread, uploadTicketFile } from "../api/supportApi";
import { useSupportStore } from "../hooks/useSupportStore";
import "../styles/support.css";

interface FileInfo {
  file: File;
  name: string;
  size: number;
}

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
const ALLOWED_FILE = /^(image\/|application\/pdf$)/;

function fmtSize(b: number) {
  return b >= 1048576
    ? (b / 1048576).toFixed(1) + "MB"
    : Math.max(1, Math.round(b / 1024)) + "KB";
}

/** 파일 API는 Bearer 인증이 필요해 a[href] 로는 못 받는다 → 토큰 실어 blob 으로 받아 다운로드한다. */
async function downloadTicketFile(fileId: number, name: string) {
  const token = getAccessToken();
  const res = await fetch(`${apiBase()}/file/${fileId}/content`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) throw new Error("다운로드 실패");
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/** 메시지 첨부 목록 — 클릭하면 인증 다운로드. */
function AttachmentList({ items }: { items: TicketAttachment[] }) {
  return (
    <div style={{ marginTop: 6, display: "flex", flexDirection: "column", gap: 4 }}>
      {items.map((a) => (
        <button
          key={a.id}
          type="button"
          onClick={() => void downloadTicketFile(a.id, a.name).catch(() => {})}
          style={{
            display: "inline-flex", alignItems: "center", gap: 6, background: "none", border: "none",
            padding: 0, cursor: "pointer", fontSize: 12, color: "inherit", textAlign: "left",
          }}
        >
          <Paperclip style={{ width: 13, height: 13, flex: "none" }} />
          <span style={{ textDecoration: "underline", overflowWrap: "anywhere" }}>{a.name}</span>
          <span style={{ opacity: 0.7 }}>({fmtSize(a.size)})</span>
        </button>
      ))}
    </div>
  );
}

const TICKET_STATUS_LABEL: Record<TicketStatus, string> = {
  RECEIVED: "접수됨",
  IN_PROGRESS: "처리중",
  ANSWERED: "답변완료",
  CLOSED: "종료",
};

function fmtDate(value?: string) {
  if (!value) return "";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function ContactPage() {
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("");
  const [body, setBody] = useState("");
  const [files, setFiles] = useState<FileInfo[]>([]);
  const [dragOver, setDragOver] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [fileError, setFileError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const { submitting, lastTicket, createTicket, myTickets, ticketsLoading, fetchMyTickets } = useSupportStore();
  const [submitFailed, setSubmitFailed] = useState(false);

  // 로그인 상태에서만 내 문의 내역을 조회한다(비로그인 GET 은 401).
  useEffect(() => {
    if (getAccessToken()) void fetchMyTickets();
  }, [fetchMyTickets]);

  const addFiles = (list: FileList) => {
    setFileError(null);
    const accepted: FileInfo[] = [];
    for (const f of Array.from(list)) {
      if (!ALLOWED_FILE.test(f.type)) { setFileError("이미지 또는 PDF만 첨부할 수 있어요."); continue; }
      if (f.size > MAX_FILE_SIZE) { setFileError("파일당 최대 10MB까지 첨부할 수 있어요."); continue; }
      accepted.push({ file: f, name: f.name, size: f.size });
    }
    setFiles((prev) => [...prev, ...accepted].slice(0, 5));
  };

  const canSubmit = title.trim() && category && body.trim().length >= 10;

  if (submitted) {
    return (
      <div>
        <div className="ct-pagehead">
          <h1>문의하기</h1>
        </div>
        <div className="ct-done">
          <div className="ct-done__ic"><Check /></div>
          <h3>문의가 접수되었어요</h3>
          <p>
            평균 1영업일 이내에 로그인 계정 이메일로 답변드릴게요.
            진행 상황은 마이페이지 &gt; 문의 내역에서 확인할 수 있어요.
          </p>
          <div className="ct-done__ticket">접수번호 · CT-{lastTicket?.id ?? "00000"}</div>
          <div className="ct-done__actions">
            <Link to="/support">
              <button className="ct-act">고객센터로</button>
            </Link>
            <button
              className="av-btn av-btn--ink"
              onClick={() => { setSubmitted(false); setTitle(""); setCategory(""); setBody(""); setFiles([]); setFileError(null); }}
            >
              새 문의 작성
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="ct-page">
      <div className="ct-pagehead">
        <div className="ct-pagehead__row">
          <div>
            <h1>문의하기</h1>
            <p>궁금한 점이나 불편한 점을 남겨주시면 빠르게 도와드릴게요.</p>
          </div>
          <Link to="/support">
            <button className="ct-act" style={{ fontSize: 14 }}>
              <ArrowLeft /> 고객센터
            </button>
          </Link>
        </div>
      </div>

      <div className="ct-contact">
        {/* Form */}
        <div className="ct-contact__main">
          <div className="ct-contact__field">
            <div className="ct-contact__label">
              제목 <span className="ct-contact__req">*</span>
            </div>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="문의 내용을 한 줄로 요약해주세요" maxLength={60} />
          </div>

          <div className="ct-contact__field">
            <div className="ct-contact__label">
              문의 카테고리 <span className="ct-contact__req">*</span>
            </div>
            <Select value={category} onValueChange={setCategory}>
              <SelectTrigger><SelectValue placeholder="카테고리를 선택하세요" /></SelectTrigger>
              <SelectContent>
                {CONTACT_CATEGORIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>

          <div className="ct-contact__field">
            <div className="ct-contact__label">
              내용 <span className="ct-contact__req">*</span>
              <span className="ct-contact__count">{body.length} / 2000</span>
            </div>
            <Textarea
              value={body}
              onChange={(e) => setBody(e.target.value.slice(0, 2000))}
              placeholder="문제가 발생한 상황과 시점, 사용 중인 기기/브라우저를 함께 적어주시면 더 빠르게 도와드릴 수 있어요."
              style={{ minHeight: 200 }}
              maxLength={2000}
            />
            <div className="ct-contact__hint">
              최소 10자 이상 입력해주세요. 스크린샷이 있으면 아래에 첨부해주세요.
            </div>
          </div>

          <div className="ct-contact__field">
            <div className="ct-contact__label">
              파일 첨부 <span className="ct-contact__count">선택 · 이미지·PDF · 최대 5개</span>
            </div>
            <div
              className={`ct-drop ${dragOver ? "is-over" : ""}`}
              onClick={() => inputRef.current?.click()}
              onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => { e.preventDefault(); setDragOver(false); addFiles(e.dataTransfer.files); }}
            >
              <div className="ct-drop__ic"><UploadCloud /></div>
              <div className="ct-drop__t">파일을 끌어다 놓거나 클릭해서 첨부</div>
              <div className="ct-drop__s">이미지·PDF, 파일당 최대 10MB · 최대 5개</div>
              <input ref={inputRef} type="file" multiple hidden onChange={(e) => e.target.files && addFiles(e.target.files)} />
            </div>
            {files.length > 0 && (
              <div className="ct-files">
                {files.map((f, i) => (
                  <div key={i} className="ct-file">
                    <Paperclip className="doc" />
                    <span className="ct-file__name">{f.name}</span>
                    <span className="ct-file__size">{fmtSize(f.size)}</span>
                    <button className="ct-file__rm" onClick={() => setFiles((p) => p.filter((_, j) => j !== i))}>
                      <X />
                    </button>
                  </div>
                ))}
              </div>
            )}
            {fileError && (
              <p style={{ color: "var(--destructive)", fontSize: 12, marginTop: 6 }}>{fileError}</p>
            )}
          </div>

          <div className="ct-contact__actions">
            <Link to="/support">
              <button className="ct-act">취소</button>
            </Link>
            <button
              className="av-btn av-btn--ink"
              disabled={!canSubmit || submitting || uploading}
              onClick={async () => {
                setSubmitFailed(false);
                try {
                  let attachmentFileIds: number[] | undefined;
                  if (files.length > 0) {
                    setUploading(true);
                    const uploaded = await Promise.all(files.map((f) => uploadTicketFile(f.file)));
                    attachmentFileIds = uploaded.map((u) => u.id);
                    setUploading(false);
                  }
                  await createTicket({ category, subject: title, content: body, attachmentFileIds });
                  setSubmitted(true);
                  window.scrollTo(0, 0);
                } catch {
                  setUploading(false);
                  setSubmitFailed(true);
                }
              }}
            >
              {uploading ? "첨부 업로드 중…" : submitting ? "전송 중…" : <>문의 보내기 <Send /></>}
            </button>
            {submitFailed && (
              <p style={{ color: "var(--destructive)", fontSize: 13, marginTop: 8 }}>
                문의 전송에 실패했습니다. 네트워크 연결을 확인하고 다시 시도해주세요.
              </p>
            )}
          </div>
        </div>

        {/* Info sidebar */}
        <aside className="ct-aside-card">
          <div className="ct-aside-card__hero">
            <div className="lab">평균 응답 시간</div>
            <div className="big"><Clock /> 1영업일 이내</div>
          </div>
          <div className="ct-aside-card__list">
            <div className="ct-aside-row">
              <span className="ct-aside-row__ic"><CalendarDays /></span>
              <div><div className="ct-aside-row__t">운영 시간</div><div className="ct-aside-row__v">평일 10:00 – 18:00</div></div>
            </div>
            <div className="ct-aside-row">
              <span className="ct-aside-row__ic"><Mail /></span>
              <div><div className="ct-aside-row__t">이메일</div><div className="ct-aside-row__v">redacted-4aa1cbad30049583@example.com</div></div>
            </div>
            <div className="ct-aside-row">
              <span className="ct-aside-row__ic"><MessageCircle /></span>
              <div><div className="ct-aside-row__t">카카오 채널</div><div className="ct-aside-row__v">@careertuner</div></div>
            </div>
          </div>
          <div className="ct-aside-card__foot">
            주말·공휴일에 접수된 문의는 다음 영업일에 순차적으로 답변드려요. 자주 묻는 질문에서 답을 더 빨리 찾을 수도 있어요.
          </div>
        </aside>
      </div>

      {/* 내 문의 내역 — 접수만 가능하던 흐름에 추적/답변 확인을 연결한다(인증 사용자). */}
      {getAccessToken() && (
        <section style={{ marginTop: 28 }}>
          <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 12 }}>내 문의 내역</h2>
          {ticketsLoading && (
            <p style={{ fontSize: 14, color: "var(--muted-foreground)" }}>불러오는 중…</p>
          )}
          {!ticketsLoading && myTickets.length === 0 && (
            <p style={{ fontSize: 14, color: "var(--muted-foreground)" }}>접수한 문의가 없습니다.</p>
          )}
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {myTickets.map((t) => (
              <MyTicketItem key={t.id} ticket={t} onChanged={() => void fetchMyTickets()} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

/** 내 문의 한 건 — 펼치면 전체 대화(원문+답변+추가문의)를 보여주고 추가 문의를 남길 수 있다. */
function MyTicketItem({ ticket, onChanged }: { ticket: SupportTicket; onChanged: () => void }) {
  const [open, setOpen] = useState(false);
  const [thread, setThread] = useState<TicketThread | null>(null);
  const [loading, setLoading] = useState(false);
  const [reply, setReply] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggle = async () => {
    const next = !open;
    setOpen(next);
    if (next && !thread) {
      setLoading(true);
      try {
        setThread(await getTicketThread(ticket.id));
      } catch {
        setError("대화를 불러오지 못했습니다.");
      } finally {
        setLoading(false);
      }
    }
  };

  const send = async () => {
    const content = reply.trim();
    if (!content) return;
    setSending(true);
    setError(null);
    try {
      setThread(await addTicketMessage(ticket.id, content));
      setReply("");
      onChanged();
    } catch {
      setError("추가 문의 전송에 실패했습니다.");
    } finally {
      setSending(false);
    }
  };

  const status = thread?.status ?? ticket.status;

  return (
    <div style={{ border: "1px solid var(--border)", borderRadius: 12, padding: 14 }}>
      <button
        type="button"
        onClick={() => void toggle()}
        style={{ display: "flex", width: "100%", justifyContent: "space-between", alignItems: "center", gap: 8, background: "none", border: "none", padding: 0, cursor: "pointer", textAlign: "left" }}
      >
        <span style={{ fontWeight: 600 }}>{ticket.subject}</span>
        <span
          style={{
            fontSize: 12, fontWeight: 600, padding: "2px 10px", borderRadius: 999, whiteSpace: "nowrap",
            background: status === "ANSWERED" ? "var(--primary)" : "var(--muted)",
            color: status === "ANSWERED" ? "var(--primary-foreground)" : "var(--muted-foreground)",
          }}
        >
          {TICKET_STATUS_LABEL[status] ?? status}
        </span>
      </button>
      <div style={{ fontSize: 12, color: "var(--muted-foreground)", marginTop: 4 }}>
        접수번호 CT-{ticket.id} · {fmtDate(ticket.createdAt)} · {open ? "접기" : "펼쳐서 대화 보기"}
      </div>

      {open && (
        <div style={{ marginTop: 12 }}>
          {loading && <p style={{ fontSize: 13, color: "var(--muted-foreground)" }}>대화를 불러오는 중…</p>}
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {thread?.messages.map((m) => (
              <div
                key={m.id}
                style={{
                  alignSelf: m.senderType === "ADMIN" ? "flex-start" : "flex-end",
                  maxWidth: "85%",
                  padding: 10,
                  borderRadius: 10,
                  background: m.senderType === "ADMIN" ? "var(--muted)" : "var(--primary)",
                  color: m.senderType === "ADMIN" ? "inherit" : "var(--primary-foreground)",
                }}
              >
                <div style={{ fontSize: 11, fontWeight: 600, opacity: 0.8, marginBottom: 2 }}>
                  {m.senderType === "ADMIN" ? "고객센터" : "나"} · {fmtDate(m.createdAt)}
                </div>
                <div style={{ fontSize: 14, whiteSpace: "pre-wrap" }}>{m.content}</div>
                {m.attachments && m.attachments.length > 0 && <AttachmentList items={m.attachments} />}
              </div>
            ))}
          </div>

          {/* 종료(CLOSED)된 문의는 백엔드가 추가 메시지를 400으로 거절한다(전이표: 재오픈은 ANSWERED/IN_PROGRESS만).
              사용자가 보내고 에러를 받는 동선을 없애기 위해 입력창 대신 새 문의 안내를 노출한다. */}
          {status === "CLOSED" ? (
            <p style={{ fontSize: 13, color: "var(--muted-foreground)", marginTop: 12, padding: "10px 12px", background: "var(--muted)", borderRadius: 8 }}>
              종료된 문의입니다. 추가로 도움이 필요하시면 위 양식으로 새 문의를 작성해 주세요.
            </p>
          ) : (
            <div style={{ marginTop: 10 }}>
              <textarea
                value={reply}
                onChange={(e) => setReply(e.target.value.slice(0, 2000))}
                placeholder="추가로 문의할 내용을 입력하세요"
                style={{ width: "100%", minHeight: 64, borderRadius: 8, border: "1px solid var(--border)", padding: 8, fontSize: 14, resize: "vertical" }}
              />
              {error && <p style={{ fontSize: 12, color: "var(--destructive)", marginTop: 4 }}>{error}</p>}
              <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 6 }}>
                <button
                  type="button"
                  onClick={() => void send()}
                  disabled={sending || !reply.trim()}
                  className="av-btn av-btn--ink"
                  style={{ opacity: sending || !reply.trim() ? 0.6 : 1 }}
                >
                  {sending ? "전송 중…" : "추가 문의 보내기"}
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
