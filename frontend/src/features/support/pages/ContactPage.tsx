import { useState, useRef } from "react";
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
import { CONTACT_CATEGORIES } from "../types/support";
import { useSupportStore } from "../hooks/useSupportStore";
import "../styles/support.css";

interface FileInfo {
  name: string;
  size: number;
}

function fmtSize(b: number) {
  return b >= 1048576
    ? (b / 1048576).toFixed(1) + "MB"
    : Math.max(1, Math.round(b / 1024)) + "KB";
}

export function ContactPage() {
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("");
  const [body, setBody] = useState("");
  const [files, setFiles] = useState<FileInfo[]>([]);
  const [dragOver, setDragOver] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const { submitting, lastTicket, createTicket } = useSupportStore();
  const [submitFailed, setSubmitFailed] = useState(false);

  const addFiles = (list: FileList) => {
    const next = Array.from(list).map((f) => ({ name: f.name, size: f.size }));
    setFiles((prev) => [...prev, ...next].slice(0, 5));
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
              onClick={() => { setSubmitted(false); setTitle(""); setCategory(""); setBody(""); setFiles([]); }}
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
              파일 첨부 <span className="ct-drop__soon">2차 구현 예정</span>
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
          </div>

          <div className="ct-contact__actions">
            <Link to="/support">
              <button className="ct-act">취소</button>
            </Link>
            <button
              className="av-btn av-btn--ink"
              disabled={!canSubmit || submitting}
              onClick={async () => {
                setSubmitFailed(false);
                try {
                  await createTicket({ category, subject: title, content: body });
                  setSubmitted(true);
                  window.scrollTo(0, 0);
                } catch {
                  setSubmitFailed(true);
                }
              }}
            >
              {submitting ? "전송 중…" : <>문의 보내기 <Send /></>}
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
    </div>
  );
}
