import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { BarChart3, ChevronRight, MessageSquare, RefreshCw, ScrollText, Search } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/app/components/ui/pagination";
import {
  INTERVIEW_MODES,
  getInterviewModeLabel,
  getScoreColor,
} from "@/features/interview/types/interview";
import { getAdminInterviewSessions, getAdminInterviewSummary } from "../../interviews/api";
import type { AdminInterviewSessionRow, AdminInterviewSummary } from "../../interviews/types";
import { ReportDetailDialog } from "../components/ReportDetailDialog";

function formatDateTime(value: string | null): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

/** 페이지 번호 목록 — 현재 주변 + 처음/끝 + 생략(...). */
function pageList(current: number, total: number): (number | "...")[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const out: (number | "...")[] = [1];
  if (current > 3) out.push("...");
  for (let i = Math.max(2, current - 1); i <= Math.min(total - 1, current + 1); i++) out.push(i);
  if (current < total - 2) out.push("...");
  out.push(total);
  return out;
}

/** 면접 리포트 운영 — 리포트가 생성된 세션만 모아 총점/답변률을 목록에서 보고 리포트 상세를 바로 연다.
 *  세션 관리(질문/답변·음성영상·AI 실패)는 면접 모니터링 화면 담당. */
export function AdminInterviewReportsPage() {
  const [rows, setRows] = useState<AdminInterviewSessionRow[]>([]);
  const [keyword, setKeyword] = useState("");
  const [mode, setMode] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [summary, setSummary] = useState<AdminInterviewSummary | null>(null);
  const [selected, setSelected] = useState<AdminInterviewSessionRow | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const SIZE = 20;

  const pageAvg = useMemo(() => {
    const scored = rows.filter((r) => r.totalScore !== null);
    if (scored.length === 0) return null;
    return Math.round(scored.reduce((sum, r) => sum + (r.totalScore ?? 0), 0) / scored.length);
  }, [rows]);

  const loadRows = async (p = page) => {
    setLoading(true);
    setError(null);
    try {
      const res = await getAdminInterviewSessions({ keyword, mode, hasReport: true, page: p, size: SIZE });
      setRows(res.items);
      setTotal(res.total);
    } catch (err) {
      setError(err instanceof Error ? err.message : "리포트 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const goPage = (p: number) => {
    setPage(p);
    void loadRows(p);
  };

  const loadSummary = () => {
    void getAdminInterviewSummary().then(setSummary).catch(() => undefined);
  };

  useEffect(() => {
    void loadRows();
    loadSummary();
  }, []);

  const openReport = (row: AdminInterviewSessionRow) => {
    setSelected(row);
    setDialogOpen(true);
  };

  return (
    <AdminShell
      active="interview-reports"
      breadcrumb="면접 리포트"
      title="면접 리포트 운영"
      icon={ScrollText}
      desc="리포트가 생성된 면접 세션을 모아 총평·항목별 점수·질문별 채점을 확인하고 운영 메모를 남깁니다."
      actions={
        <div className="flex gap-2">
          <Button asChild variant="outline" size="sm">
            <Link to="/admin/interviews"><MessageSquare className="size-4" /> 면접 모니터링</Link>
          </Button>
          <Button variant="outline" onClick={() => { void loadRows(); loadSummary(); }} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
        </div>
      }
    >
      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        <SummaryCard label="리포트 보유 세션" value={total.toLocaleString()} icon={ScrollText} tone="blue" />
        <SummaryCard
          label="리포트 생성률"
          value={summary && summary.totalSessions > 0 ? `${Math.round((total / summary.totalSessions) * 100)}%` : "-"}
          icon={BarChart3}
          tone="green"
        />
        <SummaryCard label="평균 총점 (현재 목록)" value={pageAvg != null ? `${pageAvg}점` : "-"} icon={MessageSquare} tone="amber" />
      </div>

      {/* 필터 */}
      <Card className="mb-4 border-slate-200 bg-card">
        <CardContent className="flex flex-wrap items-center gap-2 p-4">
          <div className="relative min-w-56 flex-1">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") goPage(1); }}
              placeholder="기업, 직무, 이메일 검색"
              className="pl-9"
            />
          </div>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant={mode === "" ? "default" : "outline"} onClick={() => setMode("")}>
              전체
            </Button>
            {INTERVIEW_MODES.map((m) => (
              <Button
                key={m.id}
                size="sm"
                variant={mode === m.id ? "default" : "outline"}
                onClick={() => setMode(m.id)}
              >
                {m.title}
              </Button>
            ))}
          </div>
          <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => goPage(1)}>
            필터 적용
          </Button>
        </CardContent>
      </Card>

      {error && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      )}

      {/* 리포트 목록 */}
      <div className="space-y-2">
        {rows.length === 0 && !loading ? (
          <div className="rounded-lg border border-dashed border-slate-200 bg-card p-8 text-center text-sm text-slate-400">
            생성된 리포트가 없습니다. 세션이 종료되면 리포트가 자동 생성됩니다.
          </div>
        ) : (
          rows.map((row) => (
            <button
              key={row.id}
              type="button"
              className="group flex w-full items-center gap-3 rounded-lg border border-slate-200 bg-card p-3 text-left transition-colors hover:border-blue-200"
              onClick={() => openReport(row)}
            >
              <div className={`flex size-11 shrink-0 flex-col items-center justify-center rounded-lg bg-slate-50 ${row.totalScore !== null ? getScoreColor(row.totalScore) : "text-slate-400"}`}>
                <span className="text-base font-black leading-none">{row.totalScore ?? "-"}</span>
                <span className="text-[10px] font-semibold text-slate-400">점</span>
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="truncate text-sm font-bold text-slate-950">
                    {row.companyName} · {row.jobTitle}
                  </span>
                  <Badge variant="outline" className="shrink-0">{getInterviewModeLabel(row.mode)}</Badge>
                </div>
                <div className="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-slate-500">
                  <span className="truncate">{row.userEmail}</span>
                  <span>· 답변 {row.answeredCount}/{row.questionCount}</span>
                  <span>· {formatDateTime(row.createdAt)}</span>
                  {row.adminMemo && <Badge variant="outline" className="text-[10px]">메모</Badge>}
                </div>
              </div>
              <ChevronRight className="size-4 shrink-0 text-slate-300 transition-colors group-hover:text-blue-500" />
            </button>
          ))
        )}
      </div>

      {total > SIZE && (
        <div className="mt-4 space-y-1">
          <Pagination>
            <PaginationContent className="flex-wrap">
              <PaginationItem>
                <PaginationPrevious
                  onClick={() => { if (page > 1) goPage(page - 1); }}
                  className={page <= 1 ? "pointer-events-none opacity-40" : "cursor-pointer"}
                />
              </PaginationItem>
              {pageList(page, Math.ceil(total / SIZE)).map((n, i) =>
                n === "..." ? (
                  <PaginationItem key={`e${i}`}>
                    <PaginationEllipsis />
                  </PaginationItem>
                ) : (
                  <PaginationItem key={n}>
                    <PaginationLink isActive={n === page} onClick={() => goPage(n)} className="cursor-pointer">
                      {n}
                    </PaginationLink>
                  </PaginationItem>
                ),
              )}
              <PaginationItem>
                <PaginationNext
                  onClick={() => { if (page < Math.ceil(total / SIZE)) goPage(page + 1); }}
                  className={page >= Math.ceil(total / SIZE) ? "pointer-events-none opacity-40" : "cursor-pointer"}
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
          <div className="text-center text-xs text-slate-400">총 {total}건</div>
        </div>
      )}

      <ReportDetailDialog row={selected} open={dialogOpen} onOpenChange={setDialogOpen} />
    </AdminShell>
  );
}

function SummaryCard({
  label,
  value,
  icon: Icon,
  tone,
}: {
  label: string;
  value: string;
  icon: typeof ScrollText;
  tone: "blue" | "green" | "amber";
}) {
  const toneClass = {
    blue: "bg-blue-50 text-blue-600",
    green: "bg-emerald-50 text-emerald-600",
    amber: "bg-amber-50 text-amber-600",
  }[tone];
  return (
    <Card className="border-slate-200 bg-card">
      <CardContent className="flex items-center justify-between p-4">
        <div className="min-w-0">
          <div className="text-xs font-semibold text-slate-500">{label}</div>
          <div className="mt-1 text-2xl font-black text-slate-950">{value}</div>
        </div>
        <div className={`flex size-10 shrink-0 items-center justify-center rounded-lg ${toneClass}`}>
          <Icon className="size-5" />
        </div>
      </CardContent>
    </Card>
  );
}
