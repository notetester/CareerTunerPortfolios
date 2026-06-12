import { useState, useEffect, useCallback } from "react";
import {
  ShieldAlert,
  Search,
  ChevronLeft,
  ChevronRight,
  RotateCcw,
  Trash2,
  Eye,
  X as XIcon,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import * as moderationApi from "../api/moderationApi";
import type { ModerationItem, ModerationDetail } from "../types/moderation";

type StatusFilter = "" | "HIDDEN" | "PUBLISHED" | "DELETED";

const STATUS_LABELS: Record<string, { text: string; cls: string }> = {
  HIDDEN: { text: "숨김", cls: "av-st--warn" },
  PUBLISHED: { text: "정상", cls: "av-st--ok" },
  DELETED: { text: "삭제", cls: "av-st--off" },
  PENDING: { text: "대기", cls: "av-st--info" },
};

const CATEGORY_LABELS: Record<string, string> = {
  normal: "정상",
  abuse: "욕설/비하",
  spam: "스팸",
  ad: "광고",
};

export default function AdminModerationPage() {
  const [items, setItems] = useState<ModerationItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("");
  const [query, setQuery] = useState("");
  const [toast, setToast] = useState<string | null>(null);
  const [detail, setDetail] = useState<ModerationDetail | null>(null);
  const [dialog, setDialog] = useState<{
    postId: number;
    title: string;
    action: "restore" | "delete";
  } | null>(null);
  const size = 20;

  const flash = (msg: string) => setToast(msg);

  const fetchList = useCallback(() => {
    moderationApi
      .getModerationList({
        status: statusFilter || undefined,
        page,
        size,
      })
      .then((res) => {
        setItems(res.items);
        setTotal(res.total);
      })
      .catch(() => flash("검열 목록을 불러오지 못했습니다."));
  }, [statusFilter, page]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(t);
  }, [toast]);

  const handleRowClick = (postId: number) => {
    moderationApi
      .getModerationDetail(postId)
      .then(setDetail)
      .catch(() => flash("상세 정보를 불러오지 못했습니다."));
  };

  const handleAction = async () => {
    if (!dialog) return;
    try {
      if (dialog.action === "restore") {
        await moderationApi.restorePost(dialog.postId);
        flash("게시글이 복원되었습니다.");
      } else {
        await moderationApi.deletePost(dialog.postId);
        flash("게시글이 삭제되었습니다.");
      }
      setDetail(null);
      fetchList();
    } catch {
      flash("처리에 실패했습니다.");
    }
    setDialog(null);
  };

  const filtered = query
    ? items.filter((r) => r.title.toLowerCase().includes(query.toLowerCase()))
    : items;

  const totalPages = Math.max(1, Math.ceil(total / size));

  const hiddenCount = items.filter((r) => r.status === "HIDDEN").length;

  return (
    <AdminShell
      active="moderation"
      breadcrumb="AI 검열"
      title="AI 검열 현황"
      icon={ShieldAlert}
      desc={`커뮤니티 AI 검열 관리 — 숨김 대기 ${hiddenCount}건`}
    >
      <div className="av-grid">
        <section className="av-panel">
          <div className="av-filters">
            <div className="av-search">
              <Search />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="게시글 제목 검색"
              />
            </div>
            <div className="right">
              <div className="av-seg">
                {(
                  [
                    ["", "전체"],
                    ["HIDDEN", "숨김"],
                    ["PUBLISHED", "정상"],
                    ["DELETED", "삭제"],
                  ] as [StatusFilter, string][]
                ).map(([val, label]) => (
                  <button
                    key={val}
                    className={statusFilter === val ? "on" : ""}
                    onClick={() => {
                      setStatusFilter(val);
                      setPage(1);
                    }}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <table className="av-table">
            <thead>
              <tr>
                <th>게시글</th>
                <th>카테고리</th>
                <th>AI 판정</th>
                <th className="r">확신도</th>
                <th>상태</th>
                <th className="r">작성일</th>
                <th className="r">검열일</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((r) => {
                const st = STATUS_LABELS[r.status] ?? {
                  text: r.status,
                  cls: "",
                };
                return (
                  <tr
                    key={r.postId}
                    style={{ cursor: "pointer" }}
                    onClick={() => handleRowClick(r.postId)}
                  >
                    <td>
                      <div className="av-cell__t">{r.title}</div>
                      <div className="av-cell__m">
                        {r.category} · {r.authorName}
                      </div>
                    </td>
                    <td className="av-muted">{r.category}</td>
                    <td>
                      {r.toxic ? (
                        <span className="av-st av-st--warn">
                          {CATEGORY_LABELS[r.aiCategory ?? ""] ??
                            r.aiCategory}
                        </span>
                      ) : (
                        <span className="av-st av-st--ok">정상</span>
                      )}
                    </td>
                    <td className="r num">
                      {(r.confidence * 100).toFixed(0)}%
                    </td>
                    <td>
                      <span className={`av-st ${st.cls}`}>{st.text}</span>
                    </td>
                    <td className="r av-muted num">
                      {formatDate(r.createdAt)}
                    </td>
                    <td className="r av-muted num">
                      {r.moderatedAt ? formatDate(r.moderatedAt) : "-"}
                    </td>
                  </tr>
                );
              })}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={7} style={{ textAlign: "center", padding: "2rem" }}>
                    검열 결과가 없습니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>

          <div className="av-foot">
            <span className="num">{total}건 중 {filtered.length}건 표시</span>
            <div className="av-pager">
              <button
                disabled={page <= 1}
                onClick={() => setPage((p) => p - 1)}
                aria-label="이전"
              >
                <ChevronLeft />
              </button>
              <span className="num">
                {page} / {totalPages}
              </span>
              <button
                disabled={page >= totalPages}
                onClick={() => setPage((p) => p + 1)}
                aria-label="다음"
              >
                <ChevronRight />
              </button>
            </div>
          </div>
        </section>

        {detail && (
          <aside className="av-rail">
            <section className="av-panel">
              <div className="av-mod__h">
                <span className="av-mod__t">검열 상세</span>
                <button
                  className="av-btn"
                  onClick={() => setDetail(null)}
                  aria-label="닫기"
                >
                  <XIcon />
                </button>
              </div>

              <div style={{ marginBottom: "1rem" }}>
                <h4 style={{ margin: "0 0 0.25rem" }}>{detail.title}</h4>
                <div className="av-muted" style={{ fontSize: "0.85rem" }}>
                  {detail.authorName} · {detail.category}
                </div>
              </div>

              <div
                style={{
                  background: "var(--av-bg-sub, #f8f9fa)",
                  padding: "0.75rem",
                  borderRadius: "0.5rem",
                  fontSize: "0.9rem",
                  maxHeight: "12rem",
                  overflow: "auto",
                  marginBottom: "1rem",
                  whiteSpace: "pre-wrap",
                }}
              >
                {detail.content}
              </div>

              <div className="av-rates" style={{ marginBottom: "1rem" }}>
                <div className="av-rate">
                  <span className="av-rate__l">유해 여부</span>
                  <span className="av-rate__v">
                    {detail.toxic ? (
                      <b style={{ color: "var(--av-warn, #e67e22)" }}>유해</b>
                    ) : (
                      <b style={{ color: "var(--av-ok, #27ae60)" }}>정상</b>
                    )}
                  </span>
                </div>
                <div className="av-rate">
                  <span className="av-rate__l">분류</span>
                  <span className="av-rate__v">
                    {CATEGORY_LABELS[detail.aiCategory ?? ""] ??
                      detail.aiCategory ??
                      "-"}
                  </span>
                </div>
                <div className="av-rate">
                  <span className="av-rate__l">확신도</span>
                  <span className="av-rate__v num">
                    {(detail.confidence * 100).toFixed(1)}%
                  </span>
                </div>
                <div className="av-rate">
                  <span className="av-rate__l">모델</span>
                  <span className="av-rate__v">{detail.model ?? "-"}</span>
                </div>
                <div className="av-rate">
                  <span className="av-rate__l">시도 횟수</span>
                  <span className="av-rate__v num">{detail.attemptCount}</span>
                </div>
              </div>

              {detail.status === "HIDDEN" && (
                <div style={{ display: "flex", gap: "0.5rem" }}>
                  <button
                    className="av-btn"
                    onClick={() =>
                      setDialog({
                        postId: detail.postId,
                        title: detail.title,
                        action: "restore",
                      })
                    }
                  >
                    <RotateCcw /> 복원
                  </button>
                  <button
                    className="av-btn"
                    onClick={() =>
                      setDialog({
                        postId: detail.postId,
                        title: detail.title,
                        action: "delete",
                      })
                    }
                  >
                    <Trash2 /> 삭제
                  </button>
                </div>
              )}

              {detail.status === "DELETED" && (
                <div style={{ display: "flex", gap: "0.5rem" }}>
                  <button
                    className="av-btn"
                    onClick={() =>
                      setDialog({
                        postId: detail.postId,
                        title: detail.title,
                        action: "restore",
                      })
                    }
                  >
                    <RotateCcw /> 복원
                  </button>
                </div>
              )}
            </section>
          </aside>
        )}
      </div>

      {dialog &&
        (() => {
          const isRestore = dialog.action === "restore";
          return (
            <ConfirmDialog
              variant={isRestore ? "warning" : "danger"}
              icon={isRestore ? <Eye /> : <Trash2 />}
              title={
                isRestore
                  ? "이 게시글을 복원할까요?"
                  : "이 게시글을 삭제할까요?"
              }
              description={
                isRestore
                  ? "복원하면 게시글이 다시 공개됩니다. 작성자에게 복원 알림이 전송됩니다."
                  : "삭제하면 게시글이 영구적으로 비공개 처리됩니다. 작성자에게 삭제 알림이 전송됩니다."
              }
              meta={[{ label: "게시글", value: dialog.title }]}
              confirmLabel={isRestore ? "복원" : "삭제"}
              cancelLabel="취소"
              onConfirm={handleAction}
              onCancel={() => setDialog(null)}
            />
          );
        })()}

      {toast && <div className="rpt-toast">{toast}</div>}
    </AdminShell>
  );
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const hh = String(d.getHours()).padStart(2, "0");
  const mi = String(d.getMinutes()).padStart(2, "0");
  return `${mm}/${dd} ${hh}:${mi}`;
}
