import { useCallback, useEffect, useMemo, useState } from "react";
import { Bell, Search, ChevronLeft, ChevronRight, RefreshCw } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import * as adminNotificationApi from "../api/adminNotificationApi";
import "./admin-notifications.css";

interface NotifRow {
  id: number;
  user: { name: string; email: string };
  type: string;
  title: string;
  message: string;
  isRead: boolean;
  readAt: string | null;
  sent: string;
  createdAt: string;
}

const CATS: Record<string, { label: string; dot: string }> = {
  ai:         { label: "AI 분석",  dot: "#2563eb" },
  interview:  { label: "면접",     dot: "#9333ea" },
  correction: { label: "첨삭",     dot: "#0d9488" },
  community:  { label: "커뮤니티", dot: "#4f46e5" },
  billing:    { label: "결제",     dot: "#16a34a" },
  notice:     { label: "공지",     dot: "#ea580c" },
  admin:      { label: "관리자",   dot: "#dc2626" },
};

const TYPE_CAT: Record<string, string> = {
  PROFILE_ANALYZED: "ai",
  JOB_ANALYSIS_COMPLETE: "ai",
  COMPANY_ANALYSIS_COMPLETE: "ai",
  FIT_ANALYSIS_COMPLETE: "ai",
  CAREER_TREND_COMPLETE: "ai",
  JOB_POSTING_EXTRACTION_SUCCEEDED: "ai",
  JOB_POSTING_EXTRACTION_FAILED: "ai",
  LOW_CONFIDENCE_REPORT: "admin",
  QUESTIONS_GENERATED: "interview",
  INTERVIEW_REPORT_READY: "interview",
  CORRECTION_COMPLETE: "correction",
  COMMENT: "community",
  COMMENT_REPLY: "community",
  COMMENT_HIDDEN: "community",
  LIKE: "community",
  POST_HIDDEN: "community",
  POST_SUMMARY_READY: "community",
  CREDIT_LOW: "billing",
  PAYMENT_COMPLETE: "billing",
  PAYMENT_SCHEDULED: "billing",
  CREDIT_RECHARGED: "billing",
  NOTICE: "notice",
  TICKET_ANSWERED: "notice",
  NEW_REPORT: "admin",
  NEW_TICKET: "admin",
  NEW_USER: "admin",
  TICKET_DRAFT_READY: "admin",
};

function relTime(ts?: string | null): string | null {
  if (!ts) return null;
  const t = new Date(ts).getTime();
  if (Number.isNaN(t)) return ts;
  const m = Math.floor(Math.max(0, Date.now() - t) / 60000);
  if (m < 1) return "방금";
  if (m < 60) return `${m}분 전`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}시간 전`;
  const d = Math.floor(h / 24);
  if (d === 1) return "어제";
  if (d < 7) return `${d}일 전`;
  return new Date(t).toLocaleDateString("ko-KR", { month: "long", day: "numeric" });
}

function dayKey(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function dayLabel(date: Date): string {
  return new Intl.DateTimeFormat("ko-KR", { weekday: "short" }).format(date).replace("요일", "");
}

function toRow(row: adminNotificationApi.AdminNotificationRow): NotifRow {
  return {
    id: row.id,
    user: {
      name: row.recipientName || "이름 없음",
      email: row.recipientEmail || `user-${row.userId}`,
    },
    type: row.type,
    title: row.title,
    message: row.message ?? "",
    isRead: row.read,
    readAt: relTime(row.readAt),
    sent: relTime(row.createdAt) ?? "",
    createdAt: row.createdAt,
  };
}

export default function AdminNotifications() {
  const [allRows, setAllRows] = useState<NotifRow[]>([]);
  const [query, setQuery] = useState("");
  const [cat, setCat] = useState("전체");
  const [read, setRead] = useState("전체");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const rows = await adminNotificationApi.getNotifications(100);
      setAllRows(rows.map(toRow));
    } catch {
      setError("알림 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const catOptions = ["전체", ...Object.values(CATS).map((c) => c.label)];
  const labelToCat = (l: string) => Object.keys(CATS).find((k) => CATS[k].label === l);

  const rows = useMemo(() => allRows.filter((r) => {
    if (cat !== "전체" && TYPE_CAT[r.type] !== labelToCat(cat)) return false;
    if (read === "읽음" && !r.isRead) return false;
    if (read === "미읽음" && r.isRead) return false;
    if (query) {
      const q = query.toLowerCase();
      const haystack = `${r.user.name} ${r.user.email} ${r.title} ${r.message}`.toLowerCase();
      if (!haystack.includes(q)) return false;
    }
    return true;
  }), [allRows, cat, query, read]);

  const stats = useMemo(() => {
    const totalSent = allRows.length;
    const readCount = allRows.filter((r) => r.isRead).length;
    const readRate = totalSent ? Math.round((readCount / totalSent) * 100) : 0;

    const days = Array.from({ length: 7 }, (_, i) => {
      const d = new Date();
      d.setHours(0, 0, 0, 0);
      d.setDate(d.getDate() - (6 - i));
      return { key: dayKey(d), d: dayLabel(d), v: 0, today: i === 6 };
    });
    const dayByKey = new Map(days.map((d) => [d.key, d]));

    const rates = Object.keys(CATS).map((key) => ({ cat: key, sent: 0, read: 0, rate: 0, low: false }));
    const rateByCat = new Map(rates.map((r) => [r.cat, r]));

    for (const row of allRows) {
      const date = new Date(row.createdAt);
      const daily = dayByKey.get(dayKey(date));
      if (daily) daily.v += 1;

      const category = TYPE_CAT[row.type] ?? "admin";
      const bucket = rateByCat.get(category);
      if (bucket) {
        bucket.sent += 1;
        if (row.isRead) bucket.read += 1;
      }
    }

    for (const bucket of rates) {
      bucket.rate = bucket.sent ? Math.round((bucket.read / bucket.sent) * 100) : 0;
      bucket.low = bucket.sent > 0 && bucket.rate < 50;
    }

    return {
      totalSent,
      readCount,
      readRate,
      unreadCount: totalSent - readCount,
      todaySent: days[6]?.v ?? 0,
      days,
      maxDay: Math.max(1, ...days.map((d) => d.v)),
      rates: rates.filter((r) => r.sent > 0),
    };
  }, [allRows]);

  return (
    <AdminShell
      active="notifications"
      breadcrumb="알림 모니터링"
      title="알림 모니터링"
      icon={Bell}
      desc="시스템이 자동 발송한 알림 현황 · 읽기 전용"
      actions={<button className="av-btn" onClick={() => void load()} disabled={loading}><RefreshCw /> 새로고침</button>}
    >
      <div className="av-metrics">
        <div className="av-met">
          <div className="av-met__l">오늘 발송</div>
          <div className="av-met__row"><span className="av-met__n num">{stats.todaySent}</span></div>
          <div className="av-met__d num">최근 목록 기준</div>
        </div>
        <div className="av-met">
          <div className="av-met__l">최근 발송</div>
          <div className="av-met__row"><span className="av-met__n num">{stats.totalSent.toLocaleString()}</span></div>
        </div>
        <div className="av-met">
          <div className="av-met__l">읽음률</div>
          <div className="av-met__row"><span className="av-met__n num">{stats.readRate}<small>%</small></span></div>
        </div>
        <div className="av-met">
          <div className="av-met__l">미읽음 누적</div>
          <div className="av-met__row"><span className="av-met__n num">{stats.unreadCount}</span></div>
        </div>
      </div>

      <div className="av-grid">
        <section className="av-panel">
          <div className="av-filters">
            <div className="av-search">
              <Search />
              <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="수신자·제목 검색" />
            </div>
            <select className="av-select" value={cat} onChange={(e) => setCat(e.target.value)}>
              {catOptions.map((c) => <option key={c}>{c}</option>)}
            </select>
            <div className="av-seg">
              {["전체", "읽음", "미읽음"].map((r) => (
                <button key={r} className={read === r ? "on" : ""} onClick={() => setRead(r)}>{r}</button>
              ))}
            </div>
          </div>

          {loading ? (
            <div className="av-empty">알림을 불러오는 중입니다</div>
          ) : error ? (
            <div className="av-empty">{error}</div>
          ) : rows.length === 0 ? (
            <div className="av-empty">조건에 맞는 알림이 없습니다</div>
          ) : (
            <table className="av-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>수신자</th>
                  <th>알림</th>
                  <th>타입</th>
                  <th>상태</th>
                  <th className="r">발송</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const c = CATS[TYPE_CAT[r.type]] ?? CATS.admin;
                  return (
                    <tr key={r.id} className={r.isRead ? "" : "unread"}>
                      <td className="av-id num">#{r.id}</td>
                      <td>
                        <div className="av-user">
                          <span className="av-user__av">{r.user.name[0]}</span>
                          <div>
                            <div className="av-user__n">{r.user.name}</div>
                            <div className="av-user__e">{r.user.email}</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <div className="av-msg">
                          <div className="av-msg__t">{r.title}</div>
                          <div className="av-msg__m">{r.message}</div>
                        </div>
                      </td>
                      <td>
                        <span className="av-type">
                          <span className="av-dot" style={{ background: c.dot }} />
                          {c.label}
                        </span>
                      </td>
                      <td>
                        {r.isRead
                          ? <span className="av-st av-st--read num">읽음{r.readAt ? ` · ${r.readAt}` : ""}</span>
                          : <span className="av-st av-st--unread">미읽음</span>}
                      </td>
                      <td className="av-time num">{r.sent}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}

          <div className="av-foot">
            <span className="num">{rows.length} / {stats.totalSent}건</span>
            <div className="av-pager">
              <button disabled aria-label="이전"><ChevronLeft /></button>
              <button disabled aria-label="다음"><ChevronRight /></button>
            </div>
          </div>
        </section>

        <aside className="av-rail">
          <section className="av-panel">
            <div className="av-mod__h">
              <span className="av-mod__t">타입별 읽음률</span>
              <span className="av-mod__s">최근 목록 기준</span>
            </div>
            <div className="av-rates">
              {stats.rates.length === 0 ? (
                <div className="av-empty">알림 데이터가 없습니다</div>
              ) : stats.rates.map((r) => {
                const c = CATS[r.cat];
                return (
                  <div className={`av-rate${r.low ? " low" : ""}`} key={r.cat}>
                    <span className="av-rate__l">
                      <span className="av-dot" style={{ background: c.dot }} />
                      {c.label}
                    </span>
                    <span className="av-rate__bar">
                      <span className="av-rate__fill" style={{ width: `${r.rate}%` }} />
                    </span>
                    <span className="av-rate__v num"><b>{r.rate}%</b> · {r.sent}</span>
                  </div>
                );
              })}
            </div>
          </section>

          <section className="av-panel">
            <div className="av-mod__h">
              <span className="av-mod__t">일별 발송량</span>
              <span className="av-mod__s">최근 7일</span>
            </div>
            <div className="av-days">
              {stats.days.map((d) => (
                <div className={`av-day${d.today ? " today" : ""}`} key={d.key}>
                  <span className="av-day__v num">{d.v}</span>
                  <span className="av-day__bar" style={{ height: Math.max(6, (d.v / stats.maxDay) * 56) }} />
                </div>
              ))}
            </div>
            <div className="av-days__xw">
              {stats.days.map((d) => <span key={d.key}>{d.today ? "오늘" : d.d}</span>)}
            </div>
          </section>
        </aside>
      </div>
    </AdminShell>
  );
}
