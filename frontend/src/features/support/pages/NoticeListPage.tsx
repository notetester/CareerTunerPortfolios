import { useState, useCallback, useEffect } from "react";
import { Pin, ChevronLeft, ChevronRight, List } from "lucide-react";
import { useSupportStore } from "../hooks/useSupportStore";
import NoticeDetailPage from "./NoticeDetailPage";
import "../styles/support.css";

const PAGE_SIZE = 6;

function tagStyle(tag: string) {
  switch (tag) {
    case "점검": return { background: "var(--warning-50)", color: "var(--warning)" };
    case "이벤트": return { background: "var(--success-50)", color: "var(--success)" };
    case "정책": return { background: "var(--danger-50)", color: "var(--destructive)" };
    default: return { background: "var(--info-50)", color: "var(--brand-blue)" };
  }
}

export default function NoticeListPage() {
  const [openId, setOpenId] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const { notices, noticeLoading, noticeError, fetchNotices } = useSupportStore();

  useEffect(() => {
    fetchNotices();
  }, [fetchNotices]);

  const sorted = [...notices].sort((a, b) => b.id - a.id);
  const pinned = sorted.filter((n) => n.isPinned);
  const normal = sorted.filter((n) => !n.isPinned);
  const totalPages = Math.max(1, Math.ceil(normal.length / PAGE_SIZE));
  const slice = normal.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  // 브라우저 뒤로가기 지원
  useEffect(() => {
    const onPopState = () => {
      setOpenId(null);
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  const handleOpen = useCallback((id: number) => {
    setOpenId(id);
    window.history.pushState({ noticeId: id }, "");
    window.scrollTo(0, 0);
  }, []);

  const handleBack = useCallback(() => {
    window.history.back();
  }, []);

  const handleNavigate = useCallback((id: number) => {
    setOpenId(id);
    window.history.replaceState({ noticeId: id }, "");
    window.scrollTo(0, 0);
  }, []);

  if (openId !== null) {
    return <NoticeDetailPage noticeId={openId} onBack={handleBack} onNavigate={handleNavigate} />;
  }

  return (
    <div className="ct-page ct-support ct-notices">
      <div className="ct-pagehead">
        <h1>공지사항</h1>
        <p>서비스 업데이트와 점검·이벤트 소식을 안내해드립니다.</p>
      </div>

      {noticeLoading && (
        <p style={{ textAlign: "center", color: "var(--muted-foreground)", padding: "48px 0" }}>불러오는 중...</p>
      )}
      {noticeError && !noticeLoading && (
        <p style={{ textAlign: "center", color: "var(--destructive)", padding: "48px 0", fontSize: 14 }}>
          공지사항을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.
        </p>
      )}
      <div className="ct-ntable">
        <div className="ct-ntable__head">
          <span>번호</span><span>제목</span><span>날짜</span>
        </div>
        {pinned.map((n) => (
          <div key={n.id} className="ct-nrow is-pinned" onClick={() => handleOpen(n.id)}>
            <div className="ct-nrow__no">
              <span className="ct-nrow__pin"><Pin /></span>
            </div>
            <div className="ct-nrow__main">
              <span className="ct-badge" style={tagStyle(n.tag)}>{n.tag}</span>
              <span className="ct-nrow__title">{n.title}</span>
            </div>
            <div className="ct-nrow__date">{n.createdAt}</div>
          </div>
        ))}
        {slice.map((n, i) => (
          <div key={n.id} className="ct-nrow" onClick={() => handleOpen(n.id)}>
            <div className="ct-nrow__no">{normal.length - ((page - 1) * PAGE_SIZE + i)}</div>
            <div className="ct-nrow__main">
              <span className="ct-badge" style={tagStyle(n.tag)}>{n.tag}</span>
              <span className="ct-nrow__title">{n.title}</span>
            </div>
            <div className="ct-nrow__date">{n.createdAt}</div>
          </div>
        ))}
      </div>

      {totalPages > 1 && (
        <div className="ct-pager">
          <button onClick={() => setPage(Math.max(1, page - 1))} disabled={page === 1}>
            <ChevronLeft />
          </button>
          {Array.from({ length: totalPages }, (_, i) => (
            <button key={i} className={page === i + 1 ? "is-on" : ""} onClick={() => setPage(i + 1)}>
              {i + 1}
            </button>
          ))}
          <button onClick={() => setPage(Math.min(totalPages, page + 1))} disabled={page === totalPages}>
            <ChevronRight />
          </button>
        </div>
      )}
    </div>
  );
}
