import { useState, useEffect, useCallback } from "react";
import {
  MessageSquareWarning, Search, ChevronLeft, ChevronRight,
  EyeOff, Trash2, X as XIcon, RotateCcw, Eye, ShieldAlert, Flag, Settings2,
} from "lucide-react";
import ModerationSettingsPanel from "../../moderation/pages/ModerationSettingsPanel";
import AdminShell from "../../../components/AdminShell";
import { type Report, REPORTS } from "../data/reportsData";
// TODO: 백엔드 연동 시 주석 해제
// import * as adminReportApi from "../api/adminReportApi";
// import * as moderationApi from "../../moderation/api/moderationApi";
import type { ModerationItem, ModerationDetail, ModerationStats } from "../../moderation/types/moderation";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import "./admin-reports.css";

/* ── 유저 신고 관련 타입/상수 ── */
type ReportFilterKey = "대기" | "처리됨" | "전체";
type ReportActionType = "HIDDEN" | "DELETED" | "DISMISSED";

const REPORT_ACTION_DIALOG: Record<ReportActionType, { variant: "warning" | "danger"; title: string; desc: string; label: string }> = {
  HIDDEN:    { variant: "warning", title: "이 콘텐츠를 숨길까요?", desc: "숨김 처리하면 사용자에게 더 이상 보이지 않습니다. 관리자는 여전히 확인할 수 있어요.", label: "숨김 처리" },
  DELETED:   { variant: "danger",  title: "이 콘텐츠를 삭제할까요?", desc: "삭제하면 게시글(또는 댓글)과 관련 데이터가 영구 제거되며 되돌릴 수 없어요.", label: "삭제" },
  DISMISSED: { variant: "warning", title: "이 신고를 기각할까요?", desc: "기각하면 신고가 처리 완료로 전환됩니다. 콘텐츠는 그대로 유지돼요.", label: "기각" },
};

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

/* ── 메인 탭 ── */
type MainTab = "reports" | "moderation" | "settings";

export default function AdminReports() {
  const [mainTab, setMainTab] = useState<MainTab>("reports");
  const [toast, setToast] = useState<string | null>(null);

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
      desc="유저 신고 및 AI 검열 통합 관리"
    >
      {/* 메인 탭 */}
      <div className="av-filters" style={{ marginBottom: "1rem" }}>
        <div className="av-seg">
          <button className={mainTab === "reports" ? "on" : ""} onClick={() => setMainTab("reports")}>
            <Flag style={{ width: 14, height: 14, marginRight: 4 }} /> 유저 신고
          </button>
          <button className={mainTab === "moderation" ? "on" : ""} onClick={() => setMainTab("moderation")}>
            <ShieldAlert style={{ width: 14, height: 14, marginRight: 4 }} /> AI 검열
          </button>
          <button className={mainTab === "settings" ? "on" : ""} onClick={() => setMainTab("settings")}>
            <Settings2 style={{ width: 14, height: 14, marginRight: 4 }} /> AI 검열 설정
          </button>
        </div>
      </div>

      {mainTab === "reports" ? (
        <ReportsPanel flash={flash} />
      ) : mainTab === "moderation" ? (
        <ModerationPanel flash={flash} />
      ) : (
        <ModerationSettingsPanel flash={flash} />
      )}

      {toast && <div className="rpt-toast">{toast}</div>}
    </AdminShell>
  );
}

/* ══════════════════════════════════════════════════════════
   유저 신고 패널 (기존 AdminReports 로직)
   ══════════════════════════════════════════════════════════ */
function ReportsPanel({ flash }: { flash: (msg: string) => void }) {
  const [items, setItems] = useState<Report[]>([]);
  const [filter, setFilter] = useState<ReportFilterKey>("대기");
  const [query, setQuery] = useState("");
  const [dialog, setDialog] = useState<{ report: Report; action: ReportActionType } | null>(null);

  useEffect(() => {
    // TODO: 백엔드 연동 시 adminReportApi.getReports().then(setItems) 로 교체
    setItems(REPORTS);
  }, [flash]);

  const handleAction = async () => {
    if (!dialog) return;
    // TODO: 백엔드 연동 시 adminReportApi.takeAction 으로 교체
    const updated: Report = { ...dialog.report, status: "resolved", action: dialog.action === "DISMISSED" ? "반려됨" : "숨김 처리됨" };
    setItems((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
    flash("처리되었습니다.");
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
    <>
      <div className="av-grid">
        <section className="av-panel">
          <div className="av-filters">
            <div className="av-search">
              <Search />
              <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="제목·사유 검색" />
            </div>
            <div className="right">
              <div className="av-seg">
                {(["대기", "처리됨", "전체"] as ReportFilterKey[]).map((f) => (
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
        const cfg = REPORT_ACTION_DIALOG[dialog.action];
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
    </>
  );
}

/* ══════════════════════════════════════════════════════════
   AI 검열 패널
   ══════════════════════════════════════════════════════════ */
function ModerationPanel({ flash }: { flash: (msg: string) => void }) {
  const [items, setItems] = useState<ModerationItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<ModerationStatusFilter>("");
  const [query, setQuery] = useState("");
  const [detail, setDetail] = useState<ModerationDetail | null>(null);
  const [stats, setStats] = useState<ModerationStats | null>(null);
  const [dialog, setDialog] = useState<{ postId: number; title: string; action: "restore" | "delete" } | null>(null);
  const size = 20;

  // TODO: 백엔드 연동 시 moderationApi.getModerationList/getModerationStats 로 교체
  const MOCK_MOD_ITEMS: ModerationItem[] = [
    { postId: 101, title: "★★★ 취업 컨설팅 100% 합격 ★★★", authorName: "익명_4821", category: "자유게시판", status: "HIDDEN", toxic: true, aiCategory: "spam", confidence: 0.94, attemptCount: 1, createdAt: "2026-06-10T10:00:00", moderatedAt: "2026-06-10T10:01:00" },
    { postId: 102, title: "면접에서 욕먹은 후기", authorName: "익명_1043", category: "면접후기", status: "PUBLISHED", toxic: false, aiCategory: "normal", confidence: 0.12, attemptCount: 1, createdAt: "2026-06-10T09:00:00", moderatedAt: "2026-06-10T09:01:00" },
    { postId: 103, title: "이 회사 절대 가지 마세요", authorName: "익명_2299", category: "취업후기", status: "HIDDEN", toxic: true, aiCategory: "abuse", confidence: 0.87, attemptCount: 1, createdAt: "2026-06-09T15:00:00", moderatedAt: "2026-06-09T15:01:00" },
    { postId: 104, title: "스터디원 모집합니다", authorName: "익명_7741", category: "자유게시판", status: "PUBLISHED", toxic: false, aiCategory: "normal", confidence: 0.05, attemptCount: 1, createdAt: "2026-06-09T12:00:00", moderatedAt: "2026-06-09T12:00:30" },
  ];

  const MOCK_STATS: ModerationStats = { categories: [{ category: "normal", count: 182 }, { category: "abuse", count: 14 }, { category: "spam", count: 9 }, { category: "ad", count: 3 }], total: 208 };

  const fetchList = useCallback(() => {
    const filtered = statusFilter ? MOCK_MOD_ITEMS.filter((i) => i.status === statusFilter) : MOCK_MOD_ITEMS;
    setItems(filtered);
    setTotal(filtered.length);
  }, [statusFilter, page, flash]);

  const fetchStats = useCallback(() => {
    setStats(MOCK_STATS);
  }, []);

  useEffect(() => { fetchList(); }, [fetchList]);
  useEffect(() => { fetchStats(); }, [fetchStats]);

  const handleRowClick = (postId: number) => {
    // TODO: 백엔드 연동 시 moderationApi.getModerationDetail(postId) 로 교체
    const item = MOCK_MOD_ITEMS.find((i) => i.postId === postId);
    if (item) {
      setDetail({ ...item, content: "게시글 본문 내용입니다. (목 데이터)", model: "gpt-4o-mini" } as ModerationDetail);
    }
  };

  const handleAction = async () => {
    if (!dialog) return;
    // TODO: 백엔드 연동 시 moderationApi.restorePost/deletePost 로 교체
    if (dialog.action === "restore") {
      flash("게시글이 복원되었습니다.");
    } else {
      flash("게시글이 삭제되었습니다.");
    }
    setDetail(null);
    fetchList();
    fetchStats();
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

              <div style={{ marginBottom: "1rem" }}>
                <h4 style={{ margin: "0 0 0.25rem" }}>{detail.title}</h4>
                <div className="av-muted" style={{ fontSize: "0.85rem" }}>{detail.authorName} · {detail.category}</div>
              </div>

              <div style={{
                background: "var(--av-bg-sub, #f8f9fa)", padding: "0.75rem", borderRadius: "0.5rem",
                fontSize: "0.9rem", maxHeight: "12rem", overflow: "auto", marginBottom: "1rem", whiteSpace: "pre-wrap",
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
                <div style={{ display: "flex", gap: "0.5rem" }}>
                  <button className="av-btn" onClick={() => setDialog({ postId: detail.postId, title: detail.title, action: "restore" })}><RotateCcw /> 복원</button>
                  <button className="av-btn" onClick={() => setDialog({ postId: detail.postId, title: detail.title, action: "delete" })}><Trash2 /> 삭제</button>
                </div>
              )}

              {detail.status === "DELETED" && (
                <div style={{ display: "flex", gap: "0.5rem" }}>
                  <button className="av-btn" onClick={() => setDialog({ postId: detail.postId, title: detail.title, action: "restore" })}><RotateCcw /> 복원</button>
                </div>
              )}
            </section>
          </aside>
        )}
      </div>

      {dialog && (() => {
        const isRestore = dialog.action === "restore";
        return (
          <ConfirmDialog
            variant={isRestore ? "warning" : "danger"}
            icon={isRestore ? <Eye /> : <Trash2 />}
            title={isRestore ? "이 게시글을 복원할까요?" : "이 게시글을 삭제할까요?"}
            description={isRestore
              ? "복원하면 게시글이 다시 공개됩니다. 작성자에게 복원 알림이 전송됩니다."
              : "삭제하면 게시글이 영구적으로 비공개 처리됩니다. 작성자에게 삭제 알림이 전송됩니다."}
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

function formatDate(iso: string): string {
  const d = new Date(iso);
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const hh = String(d.getHours()).padStart(2, "0");
  const mi = String(d.getMinutes()).padStart(2, "0");
  return `${mm}/${dd} ${hh}:${mi}`;
}
