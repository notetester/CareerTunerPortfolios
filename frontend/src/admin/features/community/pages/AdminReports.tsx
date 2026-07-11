import { useState, useEffect, useCallback } from "react";
import {
  MessageSquareWarning, Search, ChevronLeft, ChevronRight,
  EyeOff, Trash2, X as XIcon, RotateCcw, Eye, ShieldAlert, Flag, Settings2, RefreshCw,
  MessageCircle, UserX, ShieldX,
} from "lucide-react";
import ModerationSettingsPanel from "../../moderation/pages/ModerationSettingsPanel";
import AdminShell from "../../../components/AdminShell";
import { isHtmlContent, sanitizePostHtml } from "@/app/lib/postContent";
import {
  AdminListFooter,
  AdminListToolbar,
  AdminSelectionCell,
  AdminSelectionHeader,
  AdminSortableHeader,
  useAdminListTools,
  type AdminListColumn,
} from "../../../components/AdminListTools";
import { useAdminPendingCounts } from "@/admin/hooks/useAdminPendingCounts";
import { type Report } from "../data/reportsData";
import * as adminReportApi from "../api/adminReportApi";
import * as moderationApi from "../../moderation/api/moderationApi";
import type { ModerationItem, ModerationDetail, ModerationStats } from "../../moderation/types/moderation";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import { useAdminAuthorization, useAdminDomainAuthorization } from "@/admin/auth/useAdminAuthorization";
import "./admin-reports.css";

/* ── 유저 신고 관련 타입/상수 ── */
type ReportFilterKey = "대기" | "처리됨" | "전체";
type ReportActionType = adminReportApi.AdminReportAction;

const REPORT_ACTION_DIALOG: Record<ReportActionType, { variant: "warning" | "danger"; title: string; desc: string; label: string }> = {
  HIDDEN:    { variant: "warning", title: "이 콘텐츠를 숨길까요?", desc: "숨김 처리하면 사용자에게 더 이상 보이지 않습니다. 관리자는 여전히 확인할 수 있어요.", label: "숨김 처리" },
  DELETED:   { variant: "danger",  title: "이 콘텐츠를 삭제할까요?", desc: "삭제 상태로 전환되어 일반 사용자에게 보이지 않지만 감사·복구를 위해 데이터는 보존됩니다.", label: "삭제" },
  DISMISSED: { variant: "warning", title: "이 신고를 기각할까요?", desc: "기각하면 신고가 처리 완료로 전환됩니다. 콘텐츠는 그대로 유지돼요.", label: "기각" },
  BLOCK_AUTHOR: {
    variant: "danger", title: "작성자를 차단할까요?",
    desc: "콘텐츠는 그대로 두고 작성자 계정을 일정 기간 차단합니다. 작성자에게 차단 알림이 전송되고 활성 세션이 종료돼요.",
    label: "작성자 차단",
  },
  DELETE_AND_BLOCK: {
    variant: "danger", title: "콘텐츠 삭제 + 작성자 차단할까요?",
    desc: "콘텐츠를 소프트 삭제하고 작성자 계정도 일정 기간 차단합니다. 두 조치가 한 번에 적용됩니다.",
    label: "삭제+차단",
  },
};

function isDeleteReportAction(action: ReportActionType): boolean {
  return action === "DELETED" || action === "DELETE_AND_BLOCK";
}

function isAuthorBlockAction(action: ReportActionType): boolean {
  return action === "BLOCK_AUTHOR" || action === "DELETE_AND_BLOCK";
}

const REPORT_LIST_COLUMNS: AdminListColumn<Report>[] = [
  { id: "id", label: "ID", getText: (row) => row.id, sortable: true },
  { id: "target", label: "신고 대상", getText: (row) => `${row.title} ${row.cat} ${row.author}`, sortable: true },
  { id: "reason", label: "사유", getText: (row) => row.reason, sortable: true },
  { id: "count", label: "신고", getText: (row) => row.cnt, sortable: true },
  { id: "status", label: "상태", getText: (row) => (row.status === "pending" ? "대기" : "처리됨"), sortable: true },
  { id: "time", label: "접수", getText: (row) => row.time, sortable: true },
  { id: "action", label: "조치", getText: (row) => row.action ?? "", sortable: true },
];

/* ── AI 검열 관련 상수 ── */
type ModerationStatusFilter = "" | "HIDDEN" | "PUBLISHED" | "DELETED";

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

// 이미지 검열 카테고리/액션 라벨 (텍스트와 성격이 달라 별도 매핑)
const IMG_CATEGORY_LABELS: Record<string, string> = {
  normal: "정상", abuse: "유해", gross: "혐오/역겨움", ad: "광고", spam: "스팸", pii: "개인정보",
};
const IMG_ACTION_LABELS: Record<string, string> = {
  hide: "숨김", blur: "블러", allow: "통과",
};
const IMG_ACTION_COLOR: Record<string, string> = {
  hide: "#e74c3c", blur: "#e67e22", allow: "#27ae60",
};

/* ── 메인 탭 ── */
type MainTab = "reports" | "moderation" | "comment-moderation" | "settings";

/** 탭 라벨 옆 미처리 건수 배지. 0이면 표시 안 함(숫자만). */
function TabBadge({ count }: { count?: number }) {
  if (!count) return null;
  return (
    <span
      style={{
        marginLeft: 6, fontSize: 11, fontWeight: 700, lineHeight: 1,
        padding: "2px 6px", borderRadius: 9999,
        background: "var(--accent-soft)", color: "var(--primary)",
      }}
    >
      {count}
    </span>
  );
}

export default function AdminReports() {
  const authorization = useAdminAuthorization();
  const canReadReports = authorization.can("CONTENT_READ");
  const canReadModeration = authorization.can("AI_READ");
  const [mainTab, setMainTab] = useState<MainTab>("reports");
  const [toast, setToast] = useState<string | null>(null);
  const pending = useAdminPendingCounts(canReadReports);

  useEffect(() => {
    if (mainTab === "reports" && !canReadReports && canReadModeration) setMainTab("moderation");
    if (mainTab !== "reports" && !canReadModeration && canReadReports) setMainTab("reports");
  }, [canReadModeration, canReadReports, mainTab]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(t);
  }, [toast]);

  const flash = (msg: string) => setToast(msg);

  return (
    <AdminShell
      active="reports"
      breadcrumb="콘텐츠 관리"
      title="콘텐츠 관리"
      icon={MessageSquareWarning}
      desc="유저 신고 및 AI 검열(게시글·댓글) 통합 관리"
    >
      {/* 메인 탭 */}
      <div className="av-filters" style={{ marginBottom: "1rem" }}>
        <div className="av-seg">
          {canReadReports && <button className={mainTab === "reports" ? "on" : ""} onClick={() => setMainTab("reports")}>
            <Flag style={{ width: 14, height: 14, marginRight: 4 }} /> 유저 신고
            <TabBadge count={pending?.reports?.count} />
          </button>}
          {canReadModeration && <button className={mainTab === "moderation" ? "on" : ""} onClick={() => setMainTab("moderation")}>
            <ShieldAlert style={{ width: 14, height: 14, marginRight: 4 }} /> 게시글 검열
            <TabBadge count={pending?.hiddenPosts?.count} />
          </button>}
          {canReadModeration && <button className={mainTab === "comment-moderation" ? "on" : ""} onClick={() => setMainTab("comment-moderation")}>
            <MessageCircle style={{ width: 14, height: 14, marginRight: 4 }} /> 댓글 검열
            <TabBadge count={pending?.hiddenComments?.count} />
          </button>}
          {canReadModeration && <button className={mainTab === "settings" ? "on" : ""} onClick={() => setMainTab("settings")}>
            <Settings2 style={{ width: 14, height: 14, marginRight: 4 }} /> AI 검열 설정
          </button>}
        </div>
      </div>

      {mainTab === "reports" && canReadReports ? (
        <ReportsPanel flash={flash} />
      ) : mainTab === "moderation" && canReadModeration ? (
        <ModerationPanel flash={flash} />
      ) : mainTab === "comment-moderation" && canReadModeration ? (
        <CommentModerationPanel flash={flash} />
      ) : canReadModeration ? (
        <ModerationSettingsPanel flash={flash} />
      ) : null}

      {toast && <div className="rpt-toast">{toast}</div>}
    </AdminShell>
  );
}

/* ══════════════════════════════════════════════════════════
   유저 신고 패널 (기존 AdminReports 로직)
   ══════════════════════════════════════════════════════════ */
function ReportsPanel({ flash }: { flash: (msg: string) => void }) {
  const { canUpdate, canDelete } = useAdminDomainAuthorization("CONTENT");
  const authorization = useAdminAuthorization();
  const canReclassify = authorization.can("AI_CREATE");
  const canUpdateUsers = authorization.can("USER_UPDATE");
  const [items, setItems] = useState<Report[]>([]);
  const [filter, setFilter] = useState<ReportFilterKey>("대기");
  const [dialog, setDialog] = useState<{ report: Report; action: ReportActionType } | null>(null);
  // 합체: 취소 신고 재활성화(내 것) + 일괄 처리 다이얼로그(dev)
  const [reactivateTarget, setReactivateTarget] = useState<Report | null>(null);
  const [bulkDialog, setBulkDialog] = useState<ReportActionType | null>(null);
  const [detail, setDetail] = useState<Report | null>(null);
  const [reclassifying, setReclassifying] = useState(false);

  useEffect(() => {
    adminReportApi.getReports().then(setItems)
      .catch(() => flash("신고 목록을 불러오지 못했습니다."));
  }, [flash]);

  const handleReclassify = async () => {
    if (!canReclassify || !detail || reclassifying) return;
    setReclassifying(true);
    try {
      const updated = await adminReportApi.reclassify(detail.id);
      setDetail(updated);
      setItems((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
      flash("AI 재검토가 완료되었습니다.");
    } catch {
      flash("AI 재검토에 실패했습니다.");
    } finally {
      setReclassifying(false);
    }
  };

  const handleRowClick = (r: Report) => {
    adminReportApi.getReportDetail(r.id)
      .then(setDetail)
      .catch(() => flash("상세 정보를 불러오지 못했습니다."));
  };

  const handleAction = async () => {
    if (!dialog
      || (isDeleteReportAction(dialog.action) ? !canDelete : !canUpdate)
      || (isAuthorBlockAction(dialog.action) && !canUpdateUsers)) return;
    try {
      const updated = await adminReportApi.takeAction(dialog.report.id, dialog.action);
      setItems((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
      if (detail && detail.id === dialog.report.id) setDetail(updated);
      flash("처리되었습니다.");
    } catch (e) {
      // 차단 가드(본인/관리자/이미 차단 등) 메시지는 그대로 노출한다.
      flash(e instanceof Error && e.message ? e.message : "처리에 실패했습니다.");
    }
    setDialog(null);
  };

  const handleReactivate = async () => {
    if (!canUpdate || !reactivateTarget) return;
    try {
      const updated = await adminReportApi.reactivate(reactivateTarget.id);
      setItems((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
      if (detail && detail.id === reactivateTarget.id) setDetail(updated);
      flash("신고가 다시 대기 상태로 전환되었습니다.");
    } catch (e) {
      flash(e instanceof Error && e.message ? e.message : "재활성화에 실패했습니다.");
    }
    setReactivateTarget(null);
  };

  const statusFiltered = items.filter((r) => {
    if (filter === "대기" && r.status !== "pending") return false;
    if (filter === "처리됨" && r.status !== "resolved") return false;
    return true;
  });
  const list = useAdminListTools(statusFiltered, {
    columns: REPORT_LIST_COLUMNS,
    getRowId: (row) => row.id,
    defaultPageSize: 20,
    defaultSortId: "id",
    defaultSortDir: "desc",
  });
  const selectedPendingCount = list.selectedRows.filter((row) => row.status === "pending").length;

  const handleBulkAction = async () => {
    if (!bulkDialog || (isDeleteReportAction(bulkDialog) ? !canDelete : !canUpdate)) return;
    const targets = list.selectedRows.filter((row) => row.status === "pending");
    if (targets.length === 0) {
      flash("대기 상태의 신고만 일괄 처리할 수 있습니다.");
      setBulkDialog(null);
      return;
    }
    try {
      const updatedRows = await Promise.all(targets.map((row) => adminReportApi.takeAction(row.id, bulkDialog)));
      setItems((prev) => prev.map((row) => updatedRows.find((updated) => updated.id === row.id) ?? row));
      if (detail) {
        const updatedDetail = updatedRows.find((updated) => updated.id === detail.id);
        if (updatedDetail) setDetail(updatedDetail);
      }
      list.clearSelection();
      flash(`${targets.length}건이 처리되었습니다.`);
    } catch {
      flash("일괄 처리에 실패했습니다.");
    }
    setBulkDialog(null);
  };

  const maxReason = Math.max(...items.flatMap((r) => r.reasons.map((x) => x.n)), 1);
  const reasonTotals: Record<string, number> = {};
  for (const r of items) for (const x of r.reasons) reasonTotals[x.l] = (reasonTotals[x.l] ?? 0) + x.n;
  const reasonEntries = Object.entries(reasonTotals).sort((a, b) => b[1] - a[1]);
  const totalReasons = reasonEntries.reduce((a, [, n]) => a + n, 0);

  return (
    <>
      <div className="av-grid">
        <section className="av-panel">
          <div className="av-filters">
            <div className="right">
              <div className="av-seg">
                {(["대기", "처리됨", "전체"] as ReportFilterKey[]).map((f) => (
                  <button key={f} className={filter === f ? "on" : ""} onClick={() => { setFilter(f); list.clearSelection(); }}>{f}</button>
                ))}
              </div>
            </div>
          </div>

          <AdminListToolbar
            state={list}
            fileName="admin_reports"
            extraActions={(canUpdate || canDelete) ? (
              <>
                {canUpdate && <button type="button" disabled={selectedPendingCount === 0} onClick={() => setBulkDialog("HIDDEN")}>
                  <EyeOff /> 선택 숨김
                </button>}
                {canDelete && <button type="button" disabled={selectedPendingCount === 0} onClick={() => setBulkDialog("DELETED")}>
                  <Trash2 /> 선택 삭제
                </button>}
                {canUpdate && <button type="button" disabled={selectedPendingCount === 0} onClick={() => setBulkDialog("DISMISSED")}>
                  <XIcon /> 선택 기각
                </button>}
              </>
            ) : undefined}
          />

          <table className="av-table">
            <thead>
              <tr>
                <AdminSelectionHeader state={list} />
                <AdminSortableHeader state={list} columnId="id">ID</AdminSortableHeader>
                <AdminSortableHeader state={list} columnId="target">신고 대상</AdminSortableHeader>
                <AdminSortableHeader state={list} columnId="reason">사유</AdminSortableHeader>
                <AdminSortableHeader state={list} columnId="count" className="r">신고</AdminSortableHeader>
                <AdminSortableHeader state={list} columnId="status">상태</AdminSortableHeader>
                <AdminSortableHeader state={list} columnId="time" className="r">접수</AdminSortableHeader>
                <th className="r">조치</th>
              </tr>
            </thead>
            <tbody>
              {list.visibleRows.map((r) => (
                <tr key={r.id} style={{ cursor: "pointer" }} onClick={() => handleRowClick(r)}>
                  <AdminSelectionCell state={list} row={r} />
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
                    {r.status === "pending" && (canUpdate || canDelete) ? (
                      <div className="rv-actions" onClick={(e) => e.stopPropagation()}>
                        {canUpdate && <button className="av-btn" title="숨김" onClick={() => setDialog({ report: r, action: "HIDDEN" })}><EyeOff /></button>}
                        {canDelete && <button className="av-btn" title="삭제" onClick={() => setDialog({ report: r, action: "DELETED" })}><Trash2 /></button>}
                        {canUpdate && canUpdateUsers && <button className="av-btn" title="작성자 차단" onClick={() => setDialog({ report: r, action: "BLOCK_AUTHOR" })}><UserX /></button>}
                        {canDelete && canUpdateUsers && <button className="av-btn" title="삭제+차단" onClick={() => setDialog({ report: r, action: "DELETE_AND_BLOCK" })}><ShieldX /></button>}
                        {canUpdate && <button className="av-btn" title="기각" onClick={() => setDialog({ report: r, action: "DISMISSED" })}><XIcon /></button>}
                      </div>
                    ) : r.status !== "pending" && canUpdate ? (
                      <div className="rv-actions" onClick={(e) => e.stopPropagation()}>
                        <button className="av-btn" title="재활성화(대기 복원)" onClick={() => setReactivateTarget(r)}><RotateCcw /></button>
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
              {list.visibleRows.length === 0 && (
                <tr>
                  <td colSpan={8} style={{ textAlign: "center", padding: "2rem" }}>현재 조건에 맞는 신고가 없습니다.</td>
                </tr>
              )}
            </tbody>
          </table>

          <AdminListFooter state={list} />
        </section>

        {!detail && (
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
        )}

        {detail && (
          <aside className="av-rail">
            <section className="av-panel">
              <div className="av-mod__h">
                <span className="av-mod__t">신고 상세</span>
                <button className="av-btn" onClick={() => setDetail(null)} aria-label="닫기"><XIcon /></button>
              </div>

              <div style={{ padding: "12px 16px 0" }}>
                <h4 style={{ margin: "0 0 4px", fontSize: "13px" }}>{detail.title}</h4>
                <div className="av-muted" style={{ fontSize: "11.5px" }}>{detail.cat} · {detail.author}</div>
              </div>

              <div style={{
                margin: "10px 16px", padding: "10px 12px", borderRadius: "8px",
                background: "var(--muted)",
                fontSize: "12.5px", maxHeight: "8rem", overflow: "auto", whiteSpace: "pre-wrap",
              }}>
                {detail.excerpt}
              </div>

              {/* 신고 사유 분포 */}
              {detail.reasons.length > 0 && (
                <>
                  <div className="av-mod__h" style={{ paddingTop: "4px" }}>
                    <span className="av-mod__t">신고 사유</span>
                    <span className="av-mod__s">{detail.cnt}건</span>
                  </div>
                  <div className="av-rates" style={{ paddingTop: "8px", paddingBottom: "8px" }}>
                    {detail.reasons.map((r) => (
                      <div className="av-rate" key={r.l}>
                        <span className="av-rate__l">{r.l}</span>
                        <span className="av-rate__v num"><b>{r.n}</b>건</span>
                      </div>
                    ))}
                  </div>
                </>
              )}

              {/* AI 소견 */}
              {detail.type === "게시글" && (
                <>
                  <div className="av-mod__h" style={{ paddingTop: "4px" }}>
                    <span className="av-mod__t">AI 소견</span>
                    {detail.aiOpinion?.status === "COMPLETED" && (
                      <span className="av-mod__s">{detail.aiOpinion.model}</span>
                    )}
                  </div>
                  <div style={{ padding: "8px 16px 12px" }}>
                    {!detail.aiOpinion && (
                      <span className="av-muted" style={{ fontSize: "12px" }}>분석 결과 없음</span>
                    )}
                    {detail.aiOpinion?.status === "PENDING" && (
                      <span className="av-muted" style={{ fontSize: "12px" }}>분석 중…</span>
                    )}
                    {detail.aiOpinion?.status === "FAILED" && (
                      <span style={{ fontSize: "12px", color: "var(--av-warn, #e67e22)", wordBreak: "break-word" }}>
                        분석 실패{detail.aiOpinion.errorMessage ? `: ${detail.aiOpinion.errorMessage}` : ""}
                      </span>
                    )}
                    {detail.aiOpinion?.status === "UNMODERATED" && (
                      <span className="av-muted" style={{ fontSize: "12px", wordBreak: "break-word" }}>
                        판정 불성립 — 재검열 대기{detail.aiOpinion.errorMessage ? ` (${detail.aiOpinion.errorMessage})` : ""}
                      </span>
                    )}
                    {detail.aiOpinion?.status === "COMPLETED" && (
                      <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                        <div className="av-rate">
                          <span className="av-rate__l">판정</span>
                          <span className="av-rate__v">
                            {detail.aiOpinion.toxic
                              ? <span className="av-st av-st--warn">{CATEGORY_LABELS[detail.aiOpinion.category ?? ""] ?? detail.aiOpinion.category}</span>
                              : <span className="av-st av-st--ok">정상</span>}
                          </span>
                        </div>
                        <div className="av-rate">
                          <span className="av-rate__l">신뢰도</span>
                          <span className="av-rate__v num">{((detail.aiOpinion.confidence ?? 0) * 100).toFixed(0)}%</span>
                        </div>
                        {detail.aiOpinion.elapsedMs != null && (
                          <div className="av-rate">
                            <span className="av-rate__l">소요시간</span>
                            <span className="av-rate__v num">
                              {detail.aiOpinion.elapsedMs >= 1000
                                ? `${(detail.aiOpinion.elapsedMs / 1000).toFixed(1)}초`
                                : `${detail.aiOpinion.elapsedMs}ms`}
                            </span>
                          </div>
                        )}
                      </div>
                    )}
                    {detail.aiOpinion?.status !== "PENDING" && canReclassify && (
                      <button
                        className="av-btn"
                        style={{ marginTop: "8px", fontSize: "12px" }}
                        disabled={reclassifying}
                        onClick={handleReclassify}
                      >
                        <RefreshCw style={{ width: 13, height: 13, animation: reclassifying ? "spin 1s linear infinite" : undefined }} />
                        {reclassifying ? "분석 중…" : "AI 재검토"}
                      </button>
                    )}
                  </div>
                </>
              )}

              {/* 액션 버튼 */}
              {detail.status === "pending" && (canUpdate || canDelete) ? (
                <div style={{ display: "flex", flexWrap: "wrap", gap: "6px", padding: "0 16px 14px" }}>
                  {canUpdate && <button className="av-btn" onClick={() => setDialog({ report: detail, action: "HIDDEN" })}><EyeOff /> 숨김</button>}
                  {canDelete && <button className="av-btn" onClick={() => setDialog({ report: detail, action: "DELETED" })}><Trash2 /> 삭제</button>}
                  {canUpdate && canUpdateUsers && <button className="av-btn" onClick={() => setDialog({ report: detail, action: "BLOCK_AUTHOR" })}><UserX /> 작성자 차단</button>}
                  {canDelete && canUpdateUsers && <button className="av-btn" onClick={() => setDialog({ report: detail, action: "DELETE_AND_BLOCK" })}><ShieldX /> 삭제+차단</button>}
                  {canUpdate && <button className="av-btn" onClick={() => setDialog({ report: detail, action: "DISMISSED" })}><XIcon /> 기각</button>}
                </div>
              ) : detail.status !== "pending" && canUpdate ? (
                <div style={{ display: "flex", gap: "6px", padding: "0 16px 14px" }}>
                  <button className="av-btn" onClick={() => setReactivateTarget(detail)}><RotateCcw /> 재활성화</button>
                </div>
              ) : null}
            </section>
          </aside>
        )}
      </div>

      {dialog
        && (isDeleteReportAction(dialog.action) ? canDelete : canUpdate)
        && (!isAuthorBlockAction(dialog.action) || canUpdateUsers)
        && (() => {
        const cfg = REPORT_ACTION_DIALOG[dialog.action];
        const icon = dialog.action === "DELETED" ? <Trash2 />
          : dialog.action === "HIDDEN" ? <EyeOff />
          : dialog.action === "BLOCK_AUTHOR" ? <UserX />
          : dialog.action === "DELETE_AND_BLOCK" ? <ShieldX />
          : <XIcon />;
        return (
          <ConfirmDialog
            variant={cfg.variant}
            icon={icon}
            title={cfg.title}
            description={cfg.desc}
            meta={[
              { label: "대상", value: dialog.report.title },
              { label: "작성자", value: dialog.report.author },
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

      {reactivateTarget && canUpdate && (
        <ConfirmDialog
          variant="warning"
          icon={<RotateCcw />}
          title="이 신고를 다시 대기 상태로 되돌릴까요?"
          description="기각(또는 신고자 취소)된 신고가 다시 검토 대기 큐로 돌아옵니다. 이미 콘텐츠 조치가 완료된 신고는 되돌릴 수 없어요."
          meta={[
            { label: "대상", value: reactivateTarget.title },
            { label: "누적 신고", value: `${reactivateTarget.cnt}건` },
          ]}
          confirmLabel="재활성화"
          cancelLabel="취소"
          onConfirm={handleReactivate}
          onCancel={() => setReactivateTarget(null)}
        />
      )}
      {bulkDialog && (isDeleteReportAction(bulkDialog) ? canDelete : canUpdate) && (() => {
        const cfg = REPORT_ACTION_DIALOG[bulkDialog];
        return (
          <ConfirmDialog
            variant={cfg.variant}
            icon={bulkDialog === "DELETED" ? <Trash2 /> : bulkDialog === "HIDDEN" ? <EyeOff /> : <XIcon />}
            title={`선택한 신고 ${selectedPendingCount}건을 처리할까요?`}
            description={cfg.desc}
            meta={[
              { label: "처리 대상", value: `${selectedPendingCount}건` },
              { label: "작업", value: cfg.label },
            ]}
            confirmLabel={cfg.label}
            cancelLabel="취소"
            onConfirm={handleBulkAction}
            onCancel={() => setBulkDialog(null)}
          />
        );
      })()}
    </>
  );
}

/* ══════════════════════════════════════════════════════════
   AI 검열 패널
   ══════════════════════════════════════════════════════════ */
function ModerationPanel({ flash }: { flash: (msg: string) => void }) {
  const { canUpdate, canDelete } = useAdminDomainAuthorization("CONTENT");
  const [items, setItems] = useState<ModerationItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<ModerationStatusFilter>("");
  const [query, setQuery] = useState("");
  const [detail, setDetail] = useState<ModerationDetail | null>(null);
  const [stats, setStats] = useState<ModerationStats | null>(null);
  const [dialog, setDialog] = useState<{ postId: number; title: string; action: "restore" | "delete" } | null>(null);
  const size = 20;

  const fetchList = useCallback(() => {
    moderationApi
      .getModerationList({ status: statusFilter || undefined, page, size })
      .then((res) => { setItems(res.items); setTotal(res.total); })
      .catch(() => flash("검열 목록을 불러오지 못했습니다."));
  }, [statusFilter, page, flash]);

  const fetchStats = useCallback(() => {
    moderationApi.getModerationStats().then(setStats).catch(() => {});
  }, []);

  useEffect(() => { fetchList(); }, [fetchList]);
  useEffect(() => { fetchStats(); }, [fetchStats]);

  const handleRowClick = (postId: number) => {
    moderationApi.getModerationDetail(postId)
      .then(setDetail)
      .catch(() => flash("상세 정보를 불러오지 못했습니다."));
  };

  const handleAction = async () => {
    if (!dialog || (dialog.action === "restore" ? !canUpdate : !canDelete)) return;
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
      fetchStats();
    } catch {
      flash("처리에 실패했습니다.");
    }
    setDialog(null);
  };

  const filtered = query
    ? items.filter((r) => r.title.toLowerCase().includes(query.toLowerCase()))
    : items;

  const totalPages = Math.max(1, Math.ceil(total / size));

  return (
    <>
      <div className="av-grid">
        <section className="av-panel">
          <div className="av-filters">
            <div className="av-search">
              <Search />
              <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="게시글 제목 검색" />
            </div>
            <div className="right">
              <div className="av-seg">
                {([["", "전체"], ["HIDDEN", "숨김"], ["PUBLISHED", "정상"], ["DELETED", "삭제"]] as [ModerationStatusFilter, string][]).map(([val, label]) => (
                  <button key={val} className={statusFilter === val ? "on" : ""} onClick={() => { setStatusFilter(val); setPage(1); }}>{label}</button>
                ))}
              </div>
            </div>
          </div>

          <table className="av-table">
            <thead>
              <tr>
                <th>게시글</th>
                <th>AI 판정</th>
                <th className="r">확신도</th>
                <th>상태</th>
                <th className="r">작성일</th>
                <th className="r">검열일</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((r) => {
                const st = STATUS_LABELS[r.status] ?? { text: r.status, cls: "" };
                return (
                  <tr key={r.postId} style={{ cursor: "pointer" }} onClick={() => handleRowClick(r.postId)}>
                    <td>
                      <div className="av-cell__t">{r.title}</div>
                      <div className="av-cell__m">{r.category} · {r.authorName}</div>
                    </td>
                    <td>
                      {r.toxic
                        ? <span className="av-st av-st--warn">{CATEGORY_LABELS[r.aiCategory ?? ""] ?? r.aiCategory}</span>
                        : <span className="av-st av-st--ok">정상</span>}
                    </td>
                    <td className="r num">{(r.confidence * 100).toFixed(0)}%</td>
                    <td><span className={`av-st ${st.cls}`}>{st.text}</span></td>
                    <td className="r av-muted num">{formatDate(r.createdAt)}</td>
                    <td className="r av-muted num">{r.moderatedAt ? formatDate(r.moderatedAt) : "-"}</td>
                  </tr>
                );
              })}
              {filtered.length === 0 && (
                <tr><td colSpan={6} style={{ textAlign: "center", padding: "2rem" }}>검열 결과가 없습니다.</td></tr>
              )}
            </tbody>
          </table>

          <div className="av-foot">
            <span className="num">{total}건 중 {filtered.length}건 표시</span>
            <div className="av-pager">
              <button disabled={page <= 1} onClick={() => setPage((p) => p - 1)} aria-label="이전"><ChevronLeft /></button>
              <span className="num">{page} / {totalPages}</span>
              <button disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)} aria-label="다음"><ChevronRight /></button>
            </div>
          </div>
        </section>

        {!detail && stats && stats.categories.length > 0 && (() => {
          const maxCnt = Math.max(...stats.categories.map((c) => c.count), 1);
          return (
            <aside className="av-rail">
              <section className="av-panel">
                <div className="av-mod__h">
                  <span className="av-mod__t">AI 판정 분포</span>
                  <span className="av-mod__s">전체 · {stats.total}건</span>
                </div>
                <div className="av-rates">
                  {stats.categories.map((c) => (
                    <div className="av-rate" key={c.category}>
                      <span className="av-rate__l">{CATEGORY_LABELS[c.category] ?? c.category}</span>
                      <span className="av-rate__bar">
                        <span className="av-rate__fill" style={{ width: `${(c.count / maxCnt) * 100}%` }} />
                      </span>
                      <span className="av-rate__v num"><b>{c.count}</b>건</span>
                    </div>
                  ))}
                </div>
              </section>
            </aside>
          );
        })()}

        {detail && (
          <aside className="av-rail">
            <section className="av-panel">
              <div className="av-mod__h">
                <span className="av-mod__t">검열 상세</span>
                <button className="av-btn" onClick={() => setDetail(null)} aria-label="닫기"><XIcon /></button>
              </div>

              <div style={{ padding: "12px 16px 0" }}>
                <h4 style={{ margin: "0 0 0.25rem" }}>{detail.title}</h4>
                <div className="av-muted" style={{ fontSize: "0.85rem" }}>{detail.authorName} · {detail.category}</div>
              </div>

              {/* 리치텍스트(TipTap) 글은 sanitize 후 HTML 렌더 — 그래야 관리자도 이미지 포함 실제 본문을 본다.
                  기존 평문 글은 줄바꿈 보존(무회귀). 댓글(아래)은 평문이라 그대로 둔다. */}
              {isHtmlContent(detail.content) ? (
                <div
                  className="admin-post-content"
                  style={{
                    background: "var(--muted)", padding: "0.75rem", borderRadius: "0.5rem",
                    fontSize: "0.9rem", maxHeight: "12rem", overflow: "auto", margin: "0 16px 1rem",
                  }}
                  dangerouslySetInnerHTML={{ __html: sanitizePostHtml(detail.content) }}
                />
              ) : (
                <div style={{
                  background: "var(--muted)", padding: "0.75rem", borderRadius: "0.5rem",
                  fontSize: "0.9rem", maxHeight: "12rem", overflow: "auto", margin: "0 16px 1rem", whiteSpace: "pre-wrap",
                }}>
                  {detail.content}
                </div>
              )}

              <div className="av-rates" style={{ marginBottom: "1rem" }}>
                <div className="av-rate">
                  <span className="av-rate__l">유해 여부</span>
                  <span className="av-rate__v">
                    {detail.toxic
                      ? <b style={{ color: "var(--av-warn, #e67e22)" }}>유해</b>
                      : <b style={{ color: "var(--av-ok, #27ae60)" }}>정상</b>}
                  </span>
                </div>
                <div className="av-rate">
                  <span className="av-rate__l">분류</span>
                  <span className="av-rate__v">{CATEGORY_LABELS[detail.aiCategory ?? ""] ?? detail.aiCategory ?? "-"}</span>
                </div>
                <div className="av-rate">
                  <span className="av-rate__l">확신도</span>
                  <span className="av-rate__v num">{(detail.confidence * 100).toFixed(1)}%</span>
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

              {/* 이미지 검열 결과(IMAGE_MODERATION) — 텍스트 분석과 별개. 썸네일+카테고리+확신도+액션 */}
              {detail.images && detail.images.length > 0 && (
                <div style={{ padding: "0 16px 16px" }}>
                  <div className="av-rate__l" style={{ marginBottom: 8, fontWeight: 600 }}>
                    이미지 분석 ({detail.images.length})
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                    {detail.images.map((img, i) => (
                      <div key={i} style={{
                        display: "flex", gap: 10, alignItems: "center",
                        padding: 8, background: "var(--muted)", borderRadius: 8,
                      }}>
                        {img.url && (
                          <img src={img.url} alt="" style={{
                            width: 56, height: 56, objectFit: "cover", borderRadius: 6, flexShrink: 0,
                          }} />
                        )}
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: "0.85rem", fontWeight: 600 }}>
                            {IMG_CATEGORY_LABELS[img.category ?? ""] ?? img.category ?? "-"}
                            {" · "}
                            {img.confidence != null ? `${(img.confidence * 100).toFixed(0)}%` : "-"}
                          </div>
                          <div style={{
                            fontSize: "0.72rem", color: "var(--av-ink-3, #94a3b8)",
                            overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
                          }}>{img.url}</div>
                        </div>
                        <span style={{
                          fontSize: "0.75rem", fontWeight: 700, flexShrink: 0,
                          color: IMG_ACTION_COLOR[img.action ?? ""] ?? "var(--av-ink-2)",
                        }}>{IMG_ACTION_LABELS[img.action ?? ""] ?? img.action ?? "-"}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {detail.status === "HIDDEN" && (
                <div style={{ display: "flex", gap: "0.5rem", padding: "0 16px 16px" }}>
                  {canUpdate && <button className="av-btn" onClick={() => setDialog({ postId: detail.postId, title: detail.title, action: "restore" })}><RotateCcw /> 복원</button>}
                  {canDelete && <button className="av-btn" onClick={() => setDialog({ postId: detail.postId, title: detail.title, action: "delete" })}><Trash2 /> 삭제</button>}
                </div>
              )}
            </section>
          </aside>
        )}
      </div>

      {dialog && (dialog.action === "restore" ? canUpdate : canDelete) && (() => {
        const isRestore = dialog.action === "restore";
        return (
          <ConfirmDialog
            variant={isRestore ? "warning" : "danger"}
            icon={isRestore ? <Eye /> : <Trash2 />}
            title={isRestore ? "이 게시글을 복원할까요?" : "이 게시글을 삭제할까요?"}
            description={isRestore
              ? "복원하면 게시글이 다시 공개됩니다. 작성자에게 복원 알림이 전송됩니다."
              : "삭제 상태로 전환되어 일반 사용자에게 보이지 않으며 감사 데이터는 보존됩니다. 작성자에게 삭제 알림이 전송됩니다."}
            meta={[{ label: "게시글", value: dialog.title }]}
            confirmLabel={isRestore ? "복원" : "삭제"}
            cancelLabel="취소"
            onConfirm={handleAction}
            onCancel={() => setDialog(null)}
          />
        );
      })()}
    </>
  );
}

/* ══════════════════════════════════════════════════════════
   댓글 AI 검열 패널 (ModerationPanel 복제 — 대상만 댓글)
   - 검열 결과는 comment_ai_result, 차단은 community_comment.status HIDDEN flip
   - 복원은 HIDDEN 댓글만(자삭 DELETED는 복원 불가). 제목 자리는 본문 미리보기.
   ══════════════════════════════════════════════════════════ */
function CommentModerationPanel({ flash }: { flash: (msg: string) => void }) {
  const { canUpdate, canDelete } = useAdminDomainAuthorization("CONTENT");
  const [items, setItems] = useState<ModerationItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<ModerationStatusFilter>("");
  const [query, setQuery] = useState("");
  const [detail, setDetail] = useState<ModerationDetail | null>(null);
  const [stats, setStats] = useState<ModerationStats | null>(null);
  const [dialog, setDialog] = useState<{ commentId: number; title: string; action: "restore" | "delete" } | null>(null);
  const size = 20;

  const fetchList = useCallback(() => {
    moderationApi
      .getCommentModerationList({ status: statusFilter || undefined, page, size })
      .then((res) => { setItems(res.items); setTotal(res.total); })
      .catch(() => flash("댓글 검열 목록을 불러오지 못했습니다."));
  }, [statusFilter, page, flash]);

  const fetchStats = useCallback(() => {
    moderationApi.getCommentModerationStats().then(setStats).catch(() => {});
  }, []);

  useEffect(() => { fetchList(); }, [fetchList]);
  useEffect(() => { fetchStats(); }, [fetchStats]);

  const handleRowClick = (commentId: number) => {
    moderationApi.getCommentModerationDetail(commentId)
      .then(setDetail)
      .catch(() => flash("상세 정보를 불러오지 못했습니다."));
  };

  const handleAction = async () => {
    if (!dialog || (dialog.action === "restore" ? !canUpdate : !canDelete)) return;
    try {
      if (dialog.action === "restore") {
        await moderationApi.restoreComment(dialog.commentId);
        flash("댓글이 복원되었습니다.");
      } else {
        await moderationApi.deleteComment(dialog.commentId);
        flash("댓글이 삭제되었습니다.");
      }
      setDetail(null);
      fetchList();
      fetchStats();
    } catch {
      flash("처리에 실패했습니다.");
    }
    setDialog(null);
  };

  const filtered = query
    ? items.filter((r) => r.title.toLowerCase().includes(query.toLowerCase()))
    : items;

  const totalPages = Math.max(1, Math.ceil(total / size));

  return (
    <>
      <div className="av-grid">
        <section className="av-panel">
          <div className="av-filters">
            <div className="av-search">
              <Search />
              <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="댓글 내용 검색" />
            </div>
            <div className="right">
              <div className="av-seg">
                {([["", "전체"], ["HIDDEN", "숨김"], ["PUBLISHED", "정상"], ["DELETED", "삭제"]] as [ModerationStatusFilter, string][]).map(([val, label]) => (
                  <button key={val} className={statusFilter === val ? "on" : ""} onClick={() => { setStatusFilter(val); setPage(1); }}>{label}</button>
                ))}
              </div>
            </div>
          </div>

          <table className="av-table">
            <thead>
              <tr>
                <th>댓글</th>
                <th>AI 판정</th>
                <th className="r">확신도</th>
                <th>상태</th>
                <th className="r">작성일</th>
                <th className="r">검열일</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((r) => {
                const st = STATUS_LABELS[r.status] ?? { text: r.status, cls: "" };
                return (
                  <tr key={r.postId} style={{ cursor: "pointer" }} onClick={() => handleRowClick(r.postId)}>
                    <td>
                      <div className="av-cell__t">{r.title}</div>
                      <div className="av-cell__m">{r.authorName}</div>
                    </td>
                    <td>
                      {r.toxic
                        ? <span className="av-st av-st--warn">{CATEGORY_LABELS[r.aiCategory ?? ""] ?? r.aiCategory}</span>
                        : <span className="av-st av-st--ok">정상</span>}
                    </td>
                    <td className="r num">{(r.confidence * 100).toFixed(0)}%</td>
                    <td><span className={`av-st ${st.cls}`}>{st.text}</span></td>
                    <td className="r av-muted num">{formatDate(r.createdAt)}</td>
                    <td className="r av-muted num">{r.moderatedAt ? formatDate(r.moderatedAt) : "-"}</td>
                  </tr>
                );
              })}
              {filtered.length === 0 && (
                <tr><td colSpan={6} style={{ textAlign: "center", padding: "2rem" }}>검열 결과가 없습니다.</td></tr>
              )}
            </tbody>
          </table>

          <div className="av-foot">
            <span className="num">{total}건 중 {filtered.length}건 표시</span>
            <div className="av-pager">
              <button disabled={page <= 1} onClick={() => setPage((p) => p - 1)} aria-label="이전"><ChevronLeft /></button>
              <span className="num">{page} / {totalPages}</span>
              <button disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)} aria-label="다음"><ChevronRight /></button>
            </div>
          </div>
        </section>

        {!detail && stats && stats.categories.length > 0 && (() => {
          const maxCnt = Math.max(...stats.categories.map((c) => c.count), 1);
          return (
            <aside className="av-rail">
              <section className="av-panel">
                <div className="av-mod__h">
                  <span className="av-mod__t">AI 판정 분포</span>
                  <span className="av-mod__s">전체 · {stats.total}건</span>
                </div>
                <div className="av-rates">
                  {stats.categories.map((c) => (
                    <div className="av-rate" key={c.category}>
                      <span className="av-rate__l">{CATEGORY_LABELS[c.category] ?? c.category}</span>
                      <span className="av-rate__bar">
                        <span className="av-rate__fill" style={{ width: `${(c.count / maxCnt) * 100}%` }} />
                      </span>
                      <span className="av-rate__v num"><b>{c.count}</b>건</span>
                    </div>
                  ))}
                </div>
              </section>
            </aside>
          );
        })()}

        {detail && (
          <aside className="av-rail">
            <section className="av-panel">
              <div className="av-mod__h">
                <span className="av-mod__t">검열 상세</span>
                <button className="av-btn" onClick={() => setDetail(null)} aria-label="닫기"><XIcon /></button>
              </div>

              <div style={{ padding: "12px 16px 0" }}>
                <h4 style={{ margin: "0 0 0.25rem" }}>{detail.authorName}님의 댓글</h4>
                <div className="av-muted" style={{ fontSize: "0.85rem" }}>댓글 #{detail.postId}</div>
              </div>

              <div style={{
                background: "var(--muted)", padding: "0.75rem", borderRadius: "0.5rem",
                fontSize: "0.9rem", maxHeight: "12rem", overflow: "auto", margin: "0 16px 1rem", whiteSpace: "pre-wrap",
              }}>
                {detail.content}
              </div>

              <div className="av-rates" style={{ marginBottom: "1rem" }}>
                <div className="av-rate">
                  <span className="av-rate__l">유해 여부</span>
                  <span className="av-rate__v">
                    {detail.toxic
                      ? <b style={{ color: "var(--av-warn, #e67e22)" }}>유해</b>
                      : <b style={{ color: "var(--av-ok, #27ae60)" }}>정상</b>}
                  </span>
                </div>
                <div className="av-rate">
                  <span className="av-rate__l">분류</span>
                  <span className="av-rate__v">{CATEGORY_LABELS[detail.aiCategory ?? ""] ?? detail.aiCategory ?? "-"}</span>
                </div>
                <div className="av-rate">
                  <span className="av-rate__l">확신도</span>
                  <span className="av-rate__v num">{(detail.confidence * 100).toFixed(1)}%</span>
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
                <div style={{ display: "flex", gap: "0.5rem", padding: "0 16px 16px" }}>
                  {canUpdate && <button className="av-btn" onClick={() => setDialog({ commentId: detail.postId, title: detail.title, action: "restore" })}><RotateCcw /> 복원</button>}
                  {canDelete && <button className="av-btn" onClick={() => setDialog({ commentId: detail.postId, title: detail.title, action: "delete" })}><Trash2 /> 삭제</button>}
                </div>
              )}
            </section>
          </aside>
        )}
      </div>

      {dialog && (dialog.action === "restore" ? canUpdate : canDelete) && (() => {
        const isRestore = dialog.action === "restore";
        return (
          <ConfirmDialog
            variant={isRestore ? "warning" : "danger"}
            icon={isRestore ? <Eye /> : <Trash2 />}
            title={isRestore ? "이 댓글을 복원할까요?" : "이 댓글을 삭제할까요?"}
            description={isRestore
              ? "복원하면 댓글이 다시 공개됩니다. 작성자에게 복원 알림이 전송됩니다."
              : "삭제 상태로 전환되어 일반 사용자에게 보이지 않으며 감사 데이터는 보존됩니다. 작성자에게 삭제 알림이 전송됩니다."}
            meta={[{ label: "댓글", value: dialog.title }]}
            confirmLabel={isRestore ? "복원" : "삭제"}
            cancelLabel="취소"
            onConfirm={handleAction}
            onCancel={() => setDialog(null)}
          />
        );
      })()}
    </>
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
