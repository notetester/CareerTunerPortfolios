import { useEffect } from "react";
import { ArrowLeft, Pin, Eye, Calendar, ChevronUp, ChevronDown, List } from "lucide-react";
import type { Notice } from "../types/support";
import { useSupportStore } from "../hooks/useSupportStore";
// 공통 리치텍스트 유틸: HTML 감지 + sanitize (마크다운 공지는 renderMarkdown 폴백)
import { isHtmlContent, sanitizePostHtml } from "@/app/lib/postContent";

function tagStyle(tag: string) {
  switch (tag) {
    case "점검": return { background: "var(--warning-50)", color: "var(--warning)" };
    case "이벤트": return { background: "var(--success-50)", color: "var(--success)" };
    case "정책": return { background: "var(--danger-50)", color: "var(--destructive)" };
    default: return { background: "var(--info-50)", color: "var(--brand-blue)" };
  }
}

function renderInline(text: string): React.ReactNode {
  const parts: React.ReactNode[] = [];
  const re = /\*\*([^*]+)\*\*/g;
  let last = 0;
  let m;
  let k = 0;
  while ((m = re.exec(text))) {
    if (m.index > last) parts.push(text.slice(last, m.index));
    parts.push(<strong key={k}>{m[1]}</strong>);
    last = m.index + m[0].length;
    k++;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts.length === 1 ? parts[0] : <>{parts}</>;
}

function renderMarkdown(md: string) {
  const lines = md.split("\n");
  const blocks: React.ReactNode[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (line.trim() === "") { i++; continue; }
    if (line.startsWith("## ")) {
      blocks.push(<h2 key={i}>{renderInline(line.slice(3))}</h2>);
      i++; continue;
    }
    if (/^[-*] /.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^[-*] /.test(lines[i])) { items.push(lines[i].replace(/^[-*] /, "")); i++; }
      blocks.push(<ul key={`ul-${i}`}>{items.map((it, x) => <li key={x}>{renderInline(it)}</li>)}</ul>);
      continue;
    }
    if (/^\d+\. /.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\d+\. /.test(lines[i])) { items.push(lines[i].replace(/^\d+\. /, "")); i++; }
      blocks.push(<ol key={`ol-${i}`}>{items.map((it, x) => <li key={x}>{renderInline(it)}</li>)}</ol>);
      continue;
    }
    const para = [line]; i++;
    while (i < lines.length && lines[i].trim() !== "" && !/^(#|[-*] |\d+\. )/.test(lines[i])) { para.push(lines[i]); i++; }
    blocks.push(<p key={`p-${i}`}>{renderInline(para.join(" "))}</p>);
  }
  return blocks;
}

interface NoticeDetailPageProps {
  noticeId: number;
  onBack: () => void;
  onNavigate: (id: number) => void;
}

export default function NoticeDetailPage({ noticeId, onBack, onNavigate }: NoticeDetailPageProps) {
  const { notices, currentNotice, noticeError, fetchNoticeDetail } = useSupportStore();

  useEffect(() => {
    fetchNoticeDetail(noticeId);
  }, [noticeId, fetchNoticeDetail]);

  const sorted = [...notices].sort((a, b) => b.id - a.id);
  const idx = sorted.findIndex((n) => n.id === noticeId);

  // currentNotice has full content from detail API; fall back to list item
  const notice = currentNotice?.id === noticeId ? currentNotice : sorted[idx];
  if (!notice) {
    return (
      <div className="ct-page ct-support">
        <button className="ct-ndetail__back" onClick={onBack}>
          <ArrowLeft /> 공지사항 목록
        </button>
        <p style={{ textAlign: "center", color: "var(--muted-foreground)", padding: "48px 0" }}>
          {noticeError ?? "공지사항을 불러올 수 없습니다."}
        </p>
      </div>
    );
  }

  const prev = sorted[idx - 1] as Notice | undefined;
  const next = sorted[idx + 1] as Notice | undefined;

  return (
    <div className="ct-page ct-support">
      <button className="ct-ndetail__back" onClick={onBack}>
        <ArrowLeft /> 공지사항 목록
      </button>

      <div className="ct-ndetail__head">
        <div className="ct-ndetail__tags">
          <span className="ct-badge" style={tagStyle(notice.tag)}>{notice.tag}</span>
          {notice.isPinned && (
            <span className="ct-badge" style={{ background: "var(--av-ink)", color: "var(--av-bg)", fontWeight: 600 }}>
              <Pin style={{ width: 12, height: 12, strokeWidth: 2 }} /> 고정
            </span>
          )}
        </div>
        <h1 className="ct-ndetail__title">{notice.title}</h1>
        <div className="ct-ndetail__meta">
          <span><Calendar />{notice.createdAt}</span>
          <span><Eye />조회 {notice.viewCount.toLocaleString()}</span>
        </div>
      </div>

      {/* HTML 공지(TipTap)면 sanitize 후 렌더, 기존 마크다운 공지면 renderMarkdown 폴백 — 무회귀 공존 */}
      {isHtmlContent(notice.content) ? (
        <div className="ct-nprose" dangerouslySetInnerHTML={{ __html: sanitizePostHtml(notice.content) }} />
      ) : (
        <div className="ct-nprose">{renderMarkdown(notice.content)}</div>
      )}

      <div className="ct-nnav">
        <div
          className={`ct-nnav__row ${prev ? "" : "is-disabled"}`}
          onClick={() => prev && onNavigate(prev.id)}
        >
          <span className="ct-nnav__dir"><ChevronUp /> 다음글</span>
          <span className="ct-nnav__t">{prev ? prev.title : "다음 글이 없습니다"}</span>
        </div>
        <div
          className={`ct-nnav__row ${next ? "" : "is-disabled"}`}
          onClick={() => next && onNavigate(next.id)}
        >
          <span className="ct-nnav__dir"><ChevronDown /> 이전글</span>
          <span className="ct-nnav__t">{next ? next.title : "이전 글이 없습니다"}</span>
        </div>
      </div>

      <div className="ct-ndetail__foot">
        <button className="ct-act" onClick={onBack}>
          <List /> 목록으로
        </button>
      </div>
    </div>
  );
}
