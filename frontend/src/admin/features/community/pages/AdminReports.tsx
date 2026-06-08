import { useState } from "react";
import {
  Inbox, CheckCheck, EyeOff, Trash2, Flag,
  ExternalLink, Eye, X, Check,
  MessageSquareWarning,
} from "lucide-react";
import { Button } from "@/app/components/ui/button";
import AdminShell from "../../../components/AdminShell";
import { REPORTS, type Report } from "../data/reportsData";
import "./admin-reports.css";

type FilterKey = "pending" | "resolved" | "all";
type ActionLabel = "숨김 처리됨" | "반려됨" | "삭제됨";

const STAT_CARDS = [
  { label: "신고 대기", value: 4, icon: Inbox, cls: "stat--amber" },
  { label: "오늘 처리", value: 8, icon: CheckCheck, cls: "stat--green" },
  { label: "숨김 처리", value: 5, icon: EyeOff, cls: "stat--slate" },
  { label: "삭제", value: 3, icon: Trash2, cls: "stat--red" },
];

const FILTERS: { key: FilterKey; label: string }[] = [
  { key: "pending", label: "대기" },
  { key: "resolved", label: "처리됨" },
  { key: "all", label: "전체" },
];

export default function AdminReports() {
  const [filter, setFilter] = useState<FilterKey>("pending");
  const [selected, setSelected] = useState<number>(REPORTS[0].id);
  const [items, setItems] = useState<Report[]>(REPORTS);

  const filtered = filter === "all" ? items : items.filter((r) => r.status === filter);
  const selectedItem = items.find((r) => r.id === selected) ?? null;

  const pendingCount = items.filter((r) => r.status === "pending").length;
  const resolvedCount = items.filter((r) => r.status === "resolved").length;
  const countMap: Record<FilterKey, number> = { pending: pendingCount, resolved: resolvedCount, all: items.length };

  const handleAction = (id: number, action: ActionLabel) => {
    setItems((prev) =>
      prev.map((r) => r.id === id ? { ...r, status: "resolved" as const, action } : r),
    );
  };

  const maxReason = selectedItem
    ? Math.max(...selectedItem.reasons.map((r) => r.n))
    : 1;

  return (
    <AdminShell
      active="reports"
      breadcrumb="게시판/신고"
      title="게시판/신고 관리"
      icon={MessageSquareWarning}
      desc="커뮤니티 게시글·댓글 신고를 검토하고 처리합니다."
    >
      {/* Stats */}
      <div className="rpt-stats">
        {STAT_CARDS.map((s) => (
          <div key={s.label} className={`rpt-stat ${s.cls}`}>
            <div className="rpt-stat__ic"><s.icon /></div>
            <div>
              <div className="rpt-stat__v">{s.value}</div>
              <div className="rpt-stat__l">{s.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Body */}
      <div className="rpt-body">
        {/* Left: queue */}
        <div className="rpt-queue">
          <div className="rpt-queue__bar">
            <div className="rpt-seg">
              {FILTERS.map((f) => (
                <button
                  key={f.key}
                  className={`rpt-seg__btn ${filter === f.key ? "is-on" : ""}`}
                  onClick={() => setFilter(f.key)}
                >
                  {f.label} <span className="rpt-seg__ct">{countMap[f.key]}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="rpt-cards">
            {filtered.map((r) => (
              <div
                key={r.id}
                className={`rpt-card ${selected === r.id ? "is-selected" : ""}`}
                onClick={() => setSelected(r.id)}
              >
                <div className="rpt-card__top">
                  <span className="rpt-card__reason">{r.reason}</span>
                  <span className="rpt-card__type">{r.type}</span>
                  <span className="rpt-card__flag"><Flag /> {r.cnt}</span>
                  <span className="rpt-card__time">{r.time}</span>
                </div>
                <div className="rpt-card__title">{r.title}</div>
                <div className="rpt-card__excerpt">{r.excerpt}</div>
                <div className="rpt-card__meta">
                  <span className="rpt-card__cat" style={{ color: `var(--cat-${r.catKey}-fg)` }}>
                    {r.cat}
                  </span>
                  <span className="rpt-card__author">· {r.author}</span>
                  {r.status === "resolved" && r.action && (
                    <span className="rpt-card__done"><Check /> {r.action}</span>
                  )}
                </div>
              </div>
            ))}
            {filtered.length === 0 && (
              <p className="rpt-empty">해당 조건의 신고가 없습니다.</p>
            )}
          </div>
        </div>

        {/* Right: detail panel */}
        <div className="rpt-detail">
          {selectedItem ? (
            selectedItem.status === "resolved" ? (
              <div className="rpt-detail__resolved">
                <Check />
                <span>처리 완료 · {selectedItem.action}</span>
              </div>
            ) : (
              <>
                {/* Preview */}
                <div className="rpt-preview">
                  <h4 className="rpt-preview__h">신고된 내용</h4>
                  <div className="rpt-preview__box">
                    <div className="rpt-preview__title">{selectedItem.title}</div>
                    <div className="rpt-preview__body">{selectedItem.excerpt}</div>
                    <div className="rpt-preview__author">{selectedItem.author}</div>
                  </div>
                </div>

                {/* Reason distribution */}
                <div className="rpt-reasons">
                  <h4 className="rpt-reasons__h">
                    신고 사유 <b>{selectedItem.reasons.reduce((a, r) => a + r.n, 0)}건</b>
                  </h4>
                  {selectedItem.reasons.map((r) => (
                    <div key={r.l} className="rpt-bar-row">
                      <span className="rpt-bar-row__l">{r.l}</span>
                      <div className="rpt-bar-row__track">
                        <div
                          className="rpt-bar-row__fill"
                          style={{ width: `${(r.n / maxReason) * 100}%` }}
                        />
                      </div>
                      <span className="rpt-bar-row__n">{r.n}</span>
                    </div>
                  ))}
                </div>

                {/* Actions */}
                <div className="rpt-actions">
                  <Button variant="outline" size="sm">
                    <ExternalLink /> 원문 보기
                  </Button>
                  <Button
                    variant="outline" size="sm"
                    onClick={() => handleAction(selectedItem.id, "숨김 처리됨")}
                  >
                    <EyeOff /> 숨김
                  </Button>
                  <Button
                    variant="ghost" size="sm"
                    onClick={() => handleAction(selectedItem.id, "반려됨")}
                  >
                    <X /> 반려
                  </Button>
                  <Button
                    variant="destructive" size="sm"
                    onClick={() => handleAction(selectedItem.id, "삭제됨")}
                  >
                    <Trash2 /> 삭제
                  </Button>
                </div>
              </>
            )
          ) : (
            <p className="rpt-empty">신고를 선택해주세요.</p>
          )}
        </div>
      </div>
    </AdminShell>
  );
}
