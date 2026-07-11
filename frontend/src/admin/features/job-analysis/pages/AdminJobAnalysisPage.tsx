import { useCallback, useEffect, useMemo, useState } from "react";
import {
  BarChart3,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  RefreshCw,
  Search,
  StickyNote,
  TriangleAlert,
  X,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/app/components/ui/collapsible";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import { parseJsonArrayOrText, parseJsonStringArray } from "@/features/applications/types/analysis";
import { getAdminJobAnalyses, getAdminJobAnalysisSummary, updateAdminJobAnalysisMemo } from "../api";
import type {
  AdminJobAnalysisQueryParams,
  AdminJobAnalysisRow,
  AdminJobAnalysisSummaryResponse,
} from "../types";
import AdminShell from "../../../components/AdminShell";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";

type BooleanFilter = "all" | "true" | "false";

const DEFAULT_LIMIT = "50";
const DEFAULT_OFFSET = "0";
const DEFAULT_SORT = "createdAt_desc";

const EMPTY_SUMMARY: AdminJobAnalysisSummaryResponse = {
  totalCount: 0,
  confirmedCount: 0,
  unconfirmedCount: 0,
  easyCount: 0,
  mediumCount: 0,
  hardCount: 0,
  unknownDifficultyCount: 0,
  memoCount: 0,
};

const DIFFICULTY_OPTIONS = [
  { value: "EASY", label: "낮음" },
  { value: "NORMAL", label: "보통" },
  { value: "HARD", label: "높음" },
];

const SORT_OPTIONS = [
  { value: "createdAt_desc", label: "생성일 최신순" },
  { value: "createdAt_asc", label: "생성일 오래된순" },
  { value: "confirmedAt_desc", label: "확정일 최신순" },
  { value: "confirmedAt_asc", label: "확정일 오래된순" },
  { value: "difficulty_desc", label: "난이도 높은순" },
  { value: "difficulty_asc", label: "난이도 낮은순" },
  { value: "companyName_asc", label: "회사명 오름차순" },
  { value: "companyName_desc", label: "회사명 내림차순" },
];

const SELECT_CLASS =
  "h-9 rounded-md border border-slate-200 bg-card px-3 text-sm font-normal text-slate-900 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100";

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function booleanFilterValue(value: BooleanFilter): boolean | null {
  if (value === "true") return true;
  if (value === "false") return false;
  return null;
}

function positiveNumberOrNull(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const number = Number(trimmed);
  return Number.isInteger(number) && number > 0 ? number : null;
}

function nonNegativeNumberOrDefault(value: string, fallback: number): number {
  const number = Number(value.trim());
  return Number.isInteger(number) && number >= 0 ? number : fallback;
}

function positiveNumberOrDefault(value: string, fallback: number): number {
  const number = Number(value.trim());
  return Number.isInteger(number) && number > 0 ? number : fallback;
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function getDifficultyLabel(value: string | null | undefined): string {
  const normalized = value?.toUpperCase();
  if (!normalized) return "미정";
  if (normalized === "EASY" || normalized === "LOW") return "낮음";
  if (normalized === "NORMAL" || normalized === "MEDIUM") return "보통";
  if (normalized === "HARD" || normalized === "HIGH") return "높음";
  return value ?? "미정";
}

function difficultyBadgeClass(value: string | null | undefined): string {
  const normalized = value?.toUpperCase();
  if (normalized === "HARD" || normalized === "HIGH") return "border-red-200 bg-red-50 text-red-700";
  if (normalized === "NORMAL" || normalized === "MEDIUM") return "border-blue-200 bg-blue-50 text-blue-700";
  if (normalized === "EASY" || normalized === "LOW") return "border-emerald-200 bg-emerald-50 text-emerald-700";
  return "border-slate-200 bg-slate-100 text-slate-600";
}

function hasAnalysisContent(value: string | null | undefined): boolean {
  const parsed = parseJsonArrayOrText(value);
  if (parsed.kind === "list") return parsed.items.length > 0;
  if (parsed.kind === "text") return parsed.text.trim().length > 0;
  return false;
}

function parsedItemCount(value: string | null | undefined): number {
  const parsed = parseJsonArrayOrText(value);
  if (parsed.kind === "list") return parsed.items.length;
  if (parsed.kind === "text") return parsed.text.trim() ? 1 : 0;
  return 0;
}

function hasAmbiguousConditions(row: AdminJobAnalysisRow): boolean {
  return hasAnalysisContent(row.ambiguousConditions);
}

function hasEvidence(row: AdminJobAnalysisRow): boolean {
  return hasAnalysisContent(row.evidence);
}

function formatPostingRevision(row: AdminJobAnalysisRow): string {
  const analysisRevision = row.jobPostingRevision == null ? "-" : `rev ${row.jobPostingRevision}`;
  const latestRevision = row.latestJobPostingRevision == null ? "-" : `rev ${row.latestJobPostingRevision}`;
  return `분석 ${analysisRevision} / 최신 ${latestRevision}`;
}

export function AdminJobAnalysisPage() {
  const { canUpdate } = useAdminDomainAuthorization("AI");
  const [rows, setRows] = useState<AdminJobAnalysisRow[]>([]);
  const [summary, setSummary] = useState<AdminJobAnalysisSummaryResponse>(EMPTY_SUMMARY);
  const [memos, setMemos] = useState<Record<number, string>>({});
  const [keyword, setKeyword] = useState("");
  const [difficulty, setDifficulty] = useState("all");
  const [confirmed, setConfirmed] = useState<BooleanFilter>("all");
  const [hasMemo, setHasMemo] = useState<BooleanFilter>("all");
  const [applicationCaseId, setApplicationCaseId] = useState("");
  const [userId, setUserId] = useState("");
  const [createdFrom, setCreatedFrom] = useState("");
  const [createdTo, setCreatedTo] = useState("");
  const [sort, setSort] = useState(DEFAULT_SORT);
  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [offset, setOffset] = useState(DEFAULT_OFFSET);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const queryParams = useMemo<AdminJobAnalysisQueryParams>(
    () => ({
      keyword: blankToNull(keyword),
      difficulty: difficulty === "all" ? null : difficulty,
      confirmed: booleanFilterValue(confirmed),
      hasMemo: booleanFilterValue(hasMemo),
      applicationCaseId: positiveNumberOrNull(applicationCaseId),
      userId: positiveNumberOrNull(userId),
      createdFrom: blankToNull(createdFrom),
      createdTo: blankToNull(createdTo),
      sort,
      limit: positiveNumberOrDefault(limit, Number(DEFAULT_LIMIT)),
      offset: nonNegativeNumberOrDefault(offset, Number(DEFAULT_OFFSET)),
    }),
    [applicationCaseId, confirmed, createdFrom, createdTo, difficulty, hasMemo, keyword, limit, offset, sort, userId],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextRows, nextSummary] = await Promise.all([
        getAdminJobAnalyses(queryParams),
        getAdminJobAnalysisSummary(queryParams),
      ]);
      setRows(nextRows);
      setSummary(nextSummary);
      setMemos(Object.fromEntries(nextRows.map((row) => [row.id, row.adminMemo ?? ""])));
      setSelectedId((current) => (current && nextRows.some((row) => row.id === current) ? current : (nextRows[0]?.id ?? null)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고 분석 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [queryParams]);

  useEffect(() => {
    void load();
  }, [load]);

  const selectedRow = rows.find((row) => row.id === selectedId) ?? rows[0] ?? null;
  const hasActiveFilter =
    keyword.trim() ||
    difficulty !== "all" ||
    confirmed !== "all" ||
    hasMemo !== "all" ||
    applicationCaseId.trim() ||
    userId.trim() ||
    createdFrom ||
    createdTo ||
    sort !== DEFAULT_SORT ||
    limit !== DEFAULT_LIMIT ||
    offset !== DEFAULT_OFFSET;

  const resetFilters = () => {
    setKeyword("");
    setDifficulty("all");
    setConfirmed("all");
    setHasMemo("all");
    setApplicationCaseId("");
    setUserId("");
    setCreatedFrom("");
    setCreatedTo("");
    setSort(DEFAULT_SORT);
    setLimit(DEFAULT_LIMIT);
    setOffset(DEFAULT_OFFSET);
  };

  const saveMemo = async (analysisId: number) => {
    if (!canUpdate) return;
    const nextMemo = memos[analysisId] ?? "";
    setSavingId(analysisId);
    setError(null);
    try {
      await updateAdminJobAnalysisMemo(analysisId, nextMemo);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "운영 메모를 저장하지 못했습니다.");
    } finally {
      setSavingId(null);
    }
  };

  return (
    <AdminShell
      active="job-analysis"
      breadcrumb="공고 분석 조회"
      title="공고 분석 조회"
      icon={BarChart3}
      desc="지원 건별 공고 분석 결과와 운영 검토 메모를 확인합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="space-y-5">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <SummaryCard label="전체 분석" value={summary.totalCount} icon={BarChart3} tone="blue" />
          <SummaryCard label="확정 완료" value={summary.confirmedCount} icon={CheckCircle2} tone="green" />
          <SummaryCard label="미확정" value={summary.unconfirmedCount} icon={TriangleAlert} tone="amber" />
          <SummaryCard label="메모 있음" value={summary.memoCount} icon={StickyNote} tone="red" />
        </div>

        <div className="rounded-lg border border-slate-200 bg-card p-4">
          <div className="grid gap-3 xl:grid-cols-[minmax(220px,1fr)_150px_150px_150px_120px_120px] xl:items-end">
            <label className="grid min-w-0 gap-1 text-xs font-semibold text-slate-500">
              검색
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <Input
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  className="bg-card pl-9 text-sm font-normal text-slate-900"
                  placeholder="회사, 공고, 사용자, 요약 검색"
                />
              </div>
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              난이도
              <select className={SELECT_CLASS} value={difficulty} onChange={(event) => setDifficulty(event.target.value)}>
                <option value="all">전체</option>
                {DIFFICULTY_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              확정
              <select className={SELECT_CLASS} value={confirmed} onChange={(event) => setConfirmed(event.target.value as BooleanFilter)}>
                <option value="all">전체</option>
                <option value="true">확정</option>
                <option value="false">미확정</option>
              </select>
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              메모
              <select className={SELECT_CLASS} value={hasMemo} onChange={(event) => setHasMemo(event.target.value as BooleanFilter)}>
                <option value="all">전체</option>
                <option value="true">있음</option>
                <option value="false">없음</option>
              </select>
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              지원 건 ID
              <Input type="number" min={1} value={applicationCaseId} onChange={(event) => setApplicationCaseId(event.target.value)} />
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              사용자 ID
              <Input type="number" min={1} value={userId} onChange={(event) => setUserId(event.target.value)} />
            </label>
          </div>
          <div className="mt-3 grid gap-3 lg:grid-cols-[150px_150px_190px_110px_110px_auto] lg:items-end">
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              생성 시작
              <Input type="date" value={createdFrom} onChange={(event) => setCreatedFrom(event.target.value)} />
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              생성 종료
              <Input type="date" value={createdTo} onChange={(event) => setCreatedTo(event.target.value)} />
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              정렬
              <select className={SELECT_CLASS} value={sort} onChange={(event) => setSort(event.target.value)}>
                {SORT_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              limit
              <Input type="number" min={1} max={200} value={limit} onChange={(event) => setLimit(event.target.value)} />
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              offset
              <Input type="number" min={0} value={offset} onChange={(event) => setOffset(event.target.value)} />
            </label>
            <Button type="button" variant="outline" onClick={resetFilters} disabled={!hasActiveFilter}>
              <X className="size-4" />
              초기화
            </Button>
          </div>
        </div>

        {loading ? (
          <LoadingBlock />
        ) : rows.length === 0 ? (
          <EmptyBlock message="검색 조건에 맞는 공고 분석이 없습니다." />
        ) : (
          <>
            <div className="hidden gap-4 lg:grid lg:grid-cols-[minmax(0,1.05fr)_minmax(380px,0.95fr)]">
              <Card className="overflow-hidden rounded-lg border-slate-200 bg-card">
                <CardContent className="p-0">
                  <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
                    <div>
                      <div className="text-sm font-semibold text-slate-900">분석 목록</div>
                      <div className="text-xs text-slate-500">{rows.length.toLocaleString()}건 표시</div>
                    </div>
                  </div>
                  <div className="max-h-[720px] overflow-auto">
                    <table className="w-full min-w-[780px] text-left text-sm">
                      <thead className="sticky top-0 z-10 border-b border-slate-200 bg-slate-50 text-xs text-slate-500">
                        <tr>
                          <th className="px-4 py-3">지원 건</th>
                          <th className="px-4 py-3">난이도</th>
                          <th className="px-4 py-3">검토</th>
                          <th className="px-4 py-3">생성일</th>
                        </tr>
                      </thead>
                      <tbody>
                        {rows.map((row) => {
                          const selected = selectedRow?.id === row.id;
                          return (
                            <tr
                              key={row.id}
                              className={`cursor-pointer border-b border-slate-100 transition-colors last:border-b-0 hover:bg-blue-50/60 ${selected ? "bg-blue-50" : ""}`}
                              onClick={() => setSelectedId(row.id)}
                            >
                              <td className="px-4 py-3 align-top">
                                <div className="max-w-[320px]">
                                  <div className="break-words font-semibold text-slate-900">{row.companyName}</div>
                                  <div className="mt-1 break-words text-sm text-slate-600">{row.jobTitle}</div>
                                  <div className="mt-2 text-xs text-slate-500">#{row.applicationCaseId} · {row.userEmail}</div>
                                </div>
                              </td>
                              <td className="px-4 py-3 align-top">
                                <Badge className={difficultyBadgeClass(row.difficulty)}>{getDifficultyLabel(row.difficulty)}</Badge>
                              </td>
                              <td className="px-4 py-3 align-top">
                                <QualityBadges row={row} />
                              </td>
                              <td className="px-4 py-3 align-top text-xs leading-5 text-slate-500">{formatDateTime(row.createdAt)}</td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </CardContent>
              </Card>

              <Card className="rounded-lg border-slate-200 bg-card">
                <CardContent className="p-5">
                  {selectedRow && (
                    <JobAnalysisDetail
                      row={selectedRow}
                      memo={memos[selectedRow.id] ?? ""}
                      onMemoChange={(value) => setMemos((current) => ({ ...current, [selectedRow.id]: value }))}
                      onSaveMemo={() => void saveMemo(selectedRow.id)}
                      saving={savingId === selectedRow.id}
                      canUpdate={canUpdate}
                    />
                  )}
                </CardContent>
              </Card>
            </div>

            <div className="space-y-3 lg:hidden">
              {rows.map((row) => {
                const expanded = expandedId === row.id;
                return (
                  <Card key={row.id} className="overflow-hidden rounded-lg border-slate-200 bg-card">
                    <CardContent className="space-y-4 p-4">
                      <div className="flex min-w-0 items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="break-words text-sm font-semibold text-slate-950">{row.companyName}</div>
                          <div className="mt-1 break-words text-sm text-slate-600">{row.jobTitle}</div>
                          <div className="mt-2 text-xs text-slate-500">#{row.applicationCaseId} · {formatDateTime(row.createdAt)}</div>
                        </div>
                        <Badge className={difficultyBadgeClass(row.difficulty)}>{getDifficultyLabel(row.difficulty)}</Badge>
                      </div>
                      <p className="line-clamp-3 text-sm leading-6 text-slate-600">{row.summary ?? "요약 없음"}</p>
                      <QualityBadges row={row} horizontal />
                      <Button
                        type="button"
                        variant="outline"
                        className="w-full"
                        onClick={() => setExpandedId(expanded ? null : row.id)}
                      >
                        {expanded ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
                        {expanded ? "상세 접기" : "상세 보기"}
                      </Button>
                      {expanded && (
                        <JobAnalysisDetail
                          row={row}
                          memo={memos[row.id] ?? ""}
                          onMemoChange={(value) => setMemos((current) => ({ ...current, [row.id]: value }))}
                          onSaveMemo={() => void saveMemo(row.id)}
                          saving={savingId === row.id}
                          canUpdate={canUpdate}
                          compact
                        />
                      )}
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          </>
        )}
      </div>
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
  value: number;
  icon: typeof BarChart3;
  tone: "blue" | "green" | "amber" | "red";
}) {
  const toneClass = {
    blue: "bg-blue-50 text-blue-700",
    green: "bg-emerald-50 text-emerald-700",
    amber: "bg-amber-50 text-amber-700",
    red: "bg-red-50 text-red-700",
  }[tone];

  return (
    <Card className="rounded-lg border-slate-200 bg-card">
      <CardContent className="flex items-center justify-between gap-3 p-4">
        <div>
          <div className="text-xs font-semibold text-slate-500">{label}</div>
          <div className="mt-1 text-2xl font-bold text-slate-950">{value.toLocaleString()}</div>
        </div>
        <div className={`flex size-10 items-center justify-center rounded-lg ${toneClass}`}>
          <Icon className="size-5" />
        </div>
      </CardContent>
    </Card>
  );
}

function LoadingBlock() {
  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1.05fr)_minmax(380px,0.95fr)]">
      <div className="h-96 animate-pulse rounded-lg bg-slate-200" />
      <div className="hidden h-96 animate-pulse rounded-lg bg-slate-200 lg:block" />
    </div>
  );
}

function EmptyBlock({ message }: { message: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-card p-8 text-center text-sm text-slate-500">
      {message}
    </div>
  );
}

function QualityBadges({ row, horizontal = false }: { row: AdminJobAnalysisRow; horizontal?: boolean }) {
  return (
    <div className={`flex ${horizontal ? "flex-row flex-wrap" : "flex-col"} items-start gap-1.5`}>
      {row.confirmedAt ? (
        <Badge className="border-emerald-200 bg-emerald-50 text-emerald-700">확정</Badge>
      ) : (
        <Badge className="border-amber-200 bg-amber-50 text-amber-700">미확정</Badge>
      )}
      <StalePostingBadge row={row} />
      {hasAmbiguousConditions(row) && (
        <Badge className="border-orange-200 bg-orange-50 text-orange-700">모호 {parsedItemCount(row.ambiguousConditions)}건</Badge>
      )}
      {!hasEvidence(row) && <Badge className="border-red-200 bg-red-50 text-red-700">근거 없음</Badge>}
    </div>
  );
}

function StalePostingBadge({ row }: { row: AdminJobAnalysisRow }) {
  if (!row.staleAgainstLatestPosting) return null;
  return (
    <Badge className="border-rose-200 bg-rose-50 text-rose-700" title={formatPostingRevision(row)}>
      공고 변경됨
    </Badge>
  );
}

function JobAnalysisDetail({
  row,
  memo,
  onMemoChange,
  onSaveMemo,
  saving,
  canUpdate,
  compact = false,
}: {
  row: AdminJobAnalysisRow;
  memo: string;
  onMemoChange: (value: string) => void;
  onSaveMemo: () => void;
  saving: boolean;
  canUpdate: boolean;
  compact?: boolean;
}) {
  return (
    <div className="min-w-0 space-y-4">
      {!compact && (
        <div className="flex min-w-0 items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="break-words text-lg font-bold text-slate-950">{row.companyName}</div>
            <div className="mt-1 break-words text-sm text-slate-600">{row.jobTitle}</div>
            <div className="mt-2 text-xs leading-5 text-slate-500">
              #{row.applicationCaseId} · {row.userEmail}
            </div>
            <div className="mt-2 flex flex-wrap gap-1.5">
              <StalePostingBadge row={row} />
            </div>
          </div>
          <Badge className={difficultyBadgeClass(row.difficulty)}>{getDifficultyLabel(row.difficulty)}</Badge>
        </div>
      )}

      <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
        <div className="text-xs font-semibold text-slate-500">요약</div>
        <p className="mt-2 whitespace-pre-line break-words text-sm leading-6 text-slate-700">{row.summary ?? "요약 없음"}</p>
      </div>

      <div className="grid gap-2 sm:grid-cols-2">
        <MetaBlock label="고용 형태" value={row.employmentType ?? "미정"} />
        <MetaBlock label="경력 수준" value={row.experienceLevel ?? "미정"} />
        <MetaBlock label="공고 revision" value={formatPostingRevision(row)} />
        <MetaBlock label="생성일" value={formatDateTime(row.createdAt)} />
        <MetaBlock label="확정일" value={row.confirmedAt ? formatDateTime(row.confirmedAt) : "미확정"} />
      </div>

      <div className="grid gap-2 sm:grid-cols-2">
        <KeywordBlock title="필수 역량" value={row.requiredSkills} />
        <KeywordBlock title="우대 역량" value={row.preferredSkills} />
      </div>

      <div className="grid gap-3">
        <TextBlock title="주요 업무" value={row.duties} />
        <TextBlock title="자격 요건" value={row.qualifications} />
        <CollapsibleTextBlock title="근거" value={row.evidence} />
        <CollapsibleTextBlock title="모호 조건" value={row.ambiguousConditions} />
      </div>

      {canUpdate ? <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
        <div className="text-xs font-semibold text-slate-500">운영 메모</div>
        <Textarea
          value={memo}
          onChange={(event) => onMemoChange(event.target.value)}
          className="min-h-24 bg-card"
          placeholder="분석 오류, 사용자 문의, 운영 판단을 기록"
        />
        <div className="flex justify-end">
          <Button type="button" size="sm" variant="outline" onClick={onSaveMemo} disabled={saving}>
            {saving && <RefreshCw className="size-4 animate-spin" />}
            메모 저장
          </Button>
        </div>
      </div> : <TextBlock title="운영 메모" value={row.adminMemo} />}
    </div>
  );
}

function MetaBlock({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{label}</div>
      <div className="mt-1 break-words text-sm font-bold text-slate-900">{value}</div>
    </div>
  );
}

function TextBlock({ title, value }: { title: string; value: string | null }) {
  return (
    <div className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{title}</div>
      <ParsedContent value={value} />
    </div>
  );
}

function CollapsibleTextBlock({ title, value }: { title: string; value: string | null }) {
  const [open, setOpen] = useState(false);
  const count = parsedItemCount(value);

  return (
    <Collapsible open={open} onOpenChange={setOpen} className="min-w-0 rounded-lg border border-slate-200 bg-slate-50">
      <CollapsibleTrigger asChild>
        <button
          type="button"
          className="flex w-full items-center justify-between gap-3 px-3 py-3 text-left text-xs font-semibold text-slate-500"
        >
          <span>{title}</span>
          <span className="ml-auto rounded-full bg-card px-2 py-0.5 text-[11px] font-semibold text-slate-500">
            {count > 0 ? `${count}건` : "없음"}
          </span>
          {open ? <ChevronUp className="size-4 text-slate-400" /> : <ChevronDown className="size-4 text-slate-400" />}
        </button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="border-t border-slate-200 px-3 pb-3 pt-0">
          <ParsedContent value={value} />
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}

function ParsedContent({ value }: { value: string | null }) {
  const parsed = parseJsonArrayOrText(value);

  if (parsed.kind === "list") {
    return (
      <ul className="mt-2 space-y-1.5 text-sm leading-6 text-slate-600">
        {parsed.items.map((item, index) => (
          <li key={`${item}-${index}`} className="flex gap-2">
            <span className="mt-2 size-1.5 shrink-0 rounded-full bg-current" />
            <span className="min-w-0 break-words">{item}</span>
          </li>
        ))}
      </ul>
    );
  }

  if (parsed.kind === "text") {
    return <p className="mt-2 whitespace-pre-line break-words text-sm leading-6 text-slate-600">{parsed.text}</p>;
  }

  return <p className="mt-2 text-sm text-slate-400">내용 없음</p>;
}

function KeywordBlock({ title, value }: { title: string; value: string | null }) {
  const items = parseJsonStringArray(value);
  return (
    <div className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{title}</div>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {items.length > 0 ? (
          items.map((item) => (
            <span key={item} className="max-w-full break-words rounded-full bg-card px-2 py-1 text-xs font-semibold leading-5 text-slate-700">
              {item}
            </span>
          ))
        ) : (
          <span className="text-sm text-slate-400">미정</span>
        )}
      </div>
    </div>
  );
}
