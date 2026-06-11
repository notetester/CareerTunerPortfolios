import { useState, useEffect } from "react";
import { MessageSquareWarning, Search, ChevronLeft, ChevronRight, EyeOff, Trash2, X as XIcon } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { type Report } from "../data/reportsData";
import * as adminReportApi from "../api/adminReportApi";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import "./admin-reports.css";

type FilterKey = "대기" | "처리됨" | "전체";
type ActionType = "HIDDEN" | "DELETED" | "DISMISSED";

const ACTION_DIALOG: Record<ActionType, { variant: "warning" | "danger"; title: string; desc: string; label: string }> = {
  HIDDEN:    { variant: "warning", title: "이 콘텐츠를 숨길까요?", desc: "숨김 처리하면 사용자에게 더 이상 보이지 않습니다. 관리자는 여전히 확인할 수 있어요.", label: "숨김 처리" },
  DELETED:   { variant: "danger",  title: "이 콘텐츠를 삭제할까요?", desc: "삭제하면 게시글(또는 댓글)과 관련 데이터가 영구 제거되며 되돌릴 수 없어요.", label: "삭제" },
  DISMISSED: { variant: "warning", title: "이 신고를 기각할까요?", desc: "기각하면 신고가 처리 완료로 전환됩니다. 콘텐츠는 그대로 유지돼요.", label: "기각" },
};

export default function AdminReports() {
  const [items, setItems] = useState<Report[]>([]);
  const [filter, setFilter] = useState<FilterKey>("대기");
  const [query, setQuery] = useState("");
  const [toast, setToast] = useState<string | null>(null);
  const [dialog, setDialog] = useState<{ report: Report; action: ActionType } | null>(null);

  useEffect(() => {
    adminReportApi.getReports().then(setItems)
      .catch(() => flash("신고 목록을 불러오지 못했습니다."));
  }, []);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(t);
  }, [toast]);

  const flash = (msg: string) => setToast(msg);

  const handleAction = async () => {
    if (!dialog) return;
    try {
      const updated = await adminReportApi.takeAction(dialog.report.id, dialog.action);
      setItems((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
      flash("처리되었습니다.");
    } catch {
      flash("처리에 실패했습니다.");
    }
    setDialog(null);
  };

  const filtered = items.filter((r) => {
    if (filter === "대기" && r.status !== "pending") return false;
    if (filter === "처리됨" && r.status !== "resolved") return false;
    if (query) {
      const q = query.toLowerCase();
      if (!r.title.toLowerCase().includes(q) && !r.reason.toLowerCase().includes(q)) return false;
    }
    return true;
  });

  const maxReason = Math.max(...items.flatMap((r) => r.reasons.map((x) => x.n)), 1);
  const reasonTotals: Record<string, number> = {};
  for (const r of items) for (const x of r.reasons) reasonTotals[x.l] = (reasonTotals[x.l] ?? 0) + x.n;
  const reasonEntries = Object.entries(reasonTotals).sort((a, b) => b[1] - a[1]);
  const totalReasons = reasonEntries.reduce((a, [, n]) => a + n, 0);

  return (
    <AdminShell
      active="reports"
      breadcrumb="게시판/신고"
      title="게시판/신고 관리"
      icon={MessageSquareWarning}
      desc={`커뮤니티 신고 검토 큐 — 대기 ${items.filter((r) => r.status === "pending").length}건`}
      actions={<button className="av-btn">자동 숨김 규칙</button>}
    >
      <div className="av-grid">
        <section className="av-panel">
          <div className="av-filters">
            <div className="av-search">
              <Search />
              <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="제목·사유 검색" />
            </div>
            <div className="right">
              <div className="av-seg">
                {(["대기", "처리됨", "전체"] as FilterKey[]).map((f) => (
                  <button key={f} className={filter === f ? "on" : ""} onClick={() => setFilter(f)}>{f}</button>
                ))}
              </div>
            </div>
          </div>

          <table className="av-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>신고 대상</th>
                <th>사유</th>
                <th className="r">신고</th>
                <th>상태</th>
                <th className="r">접수</th>
                <th className="r">조치</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((r) => (
                <tr key={r.id}>
                  <td className="av-id num">#{r.id}</td>
                  <td>
                    <div className="av-cell__t">{r.title}</div>
                    <div className="av-cell__m">{r.cat} · {r.author}{r.action ? ` — ${r.action}` : ""}</div>
                  </td>
                  <td className="av-muted" style={{ whiteSpace: "nowrap" }}>{r.reason}</td>
                  <td className={`r num rv-ct${r.cnt >= 5 ? " hot" : ""}`}>{r.cnt}</td>
                  <td>
                    {r.status === "pending"
                      ? <span className="av-st av-st--warn">대기</span>
                      : <span className="av-st av-st--off">처리됨</span>}
                  </td>
                  <td className="r av-muted num">{r.time}</td>
                  <td className="r">
                    {r.status === "pending" && (
                      <div className="rv-actions">
                        <button className="av-btn" title="숨김" onClick={() => setDialog({ report: r, action: "HIDDEN" })}><EyeOff /></button>
                        <button className="av-btn" title="삭제" onClick={() => setDialog({ report: r, action: "DELETED" })}><Trash2 /></button>
                        <button className="av-btn" title="기각" onClick={() => setDialog({ report: r, action: "DISMISSED" })}><XIcon /></button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="av-foot">
            <span className="num">{filtered.length}건 표시</span>
            <div className="av-pager">
              <button disabled aria-label="이전"><ChevronLeft /></button>
              <button aria-label="다음"><ChevronRight /></button>
            </div>
          </div>
        </section>

        <aside className="av-rail">
          <section className="av-panel">
            <div className="av-mod__h">
              <span className="av-mod__t">사유별 분포</span>
              <span className="av-mod__s">최근 30일 · {totalReasons}건</span>
            </div>
            <div className="av-rates">
              {reasonEntries.map(([label, n]) => (
                <div className="av-rate" key={label}>
                  <span className="av-rate__l">{label}</span>
                  <span className="av-rate__bar">
                    <span className="av-rate__fill" style={{ width: `${(n / maxReason) * 100}%` }} />
                  </span>
                  <span className="av-rate__v num"><b>{n}</b>건</span>
                </div>
              ))}
            </div>
            {reasonEntries[0] && (
              <div className="av-note">
                <b>{reasonEntries[0][0]}이 가장 많아요</b> — 자동 숨김 규칙 추가를 권장합니다.
              </div>
            )}
          </section>
        </aside>
      </div>

      {dialog && (() => {
        const cfg = ACTION_DIALOG[dialog.action];
        return (
          <ConfirmDialog
            variant={cfg.variant}
            icon={dialog.action === "DELETED" ? <Trash2 /> : dialog.action === "HIDDEN" ? <EyeOff /> : <XIcon />}
            title={cfg.title}
            description={cfg.desc}
            meta={[
              { label: "대상", value: dialog.report.title },
              { label: "신고 사유", value: dialog.report.reason },
              { label: "누적 신고", value: `${dialog.report.cnt}건` },
            ]}
            confirmLabel={cfg.label}
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
