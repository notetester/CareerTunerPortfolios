import { useCallback, useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Gauge, RefreshCw, RotateCcw, Search } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { getAdminBUsageLogs, getAdminBUsageSummary } from "../api";
import type {
  AdminAiUsageLogRow,
  AdminAiUsageStatus,
  AdminBUsageFeatureType,
  AdminBUsageLogQueryParams,
  AdminBUsageSummaryResponse,
} from "../types";
import AdminShell from "../../../components/AdminShell";

type UsageSort =
  | "CREATED_AT_DESC"
  | "CREATED_AT_ASC"
  | "TOKEN_USAGE_DESC"
  | "TOKEN_USAGE_ASC"
  | "CREDIT_USED_DESC"
  | "CREDIT_USED_ASC"
  | "MODEL_ASC"
  | "MODEL_DESC"
  | "FEATURE_TYPE_ASC"
  | "FEATURE_TYPE_DESC"
  | "STATUS_ASC"
  | "STATUS_DESC";

type UsageFilters = {
  featureType: AdminBUsageFeatureType | "";
  status: Extract<AdminAiUsageStatus, "SUCCESS" | "FAILED"> | "";
  keyword: string;
  applicationCaseId: string;
  userId: string;
  model: string;
  createdFrom: string;
  createdTo: string;
  sort: UsageSort;
  limit: string;
  offset: string;
};

const DEFAULT_FILTERS: UsageFilters = {
  featureType: "",
  status: "",
  keyword: "",
  applicationCaseId: "",
  userId: "",
  model: "",
  createdFrom: "",
  createdTo: "",
  sort: "CREATED_AT_DESC",
  limit: "50",
  offset: "0",
};

const EMPTY_SUMMARY: AdminBUsageSummaryResponse = {
  totalCount: 0,
  successCount: 0,
  failedCount: 0,
  tokenUsage: 0,
  creditUsed: 0,
  jobAnalysisCount: 0,
  companyResearchCount: 0,
  jobPostingOcrCount: 0,
};

const FEATURE_OPTIONS: Array<{ value: UsageFilters["featureType"]; label: string }> = [
  { value: "", label: "전체 기능" },
  { value: "JOB_ANALYSIS", label: "공고 분석" },
  { value: "COMPANY_RESEARCH", label: "기업 분석" },
  { value: "JOB_POSTING_OCR", label: "공고 OCR" },
];

const STATUS_OPTIONS: Array<{ value: UsageFilters["status"]; label: string }> = [
  { value: "", label: "전체 상태" },
  { value: "SUCCESS", label: "성공" },
  { value: "FAILED", label: "실패" },
];

const SORT_OPTIONS: Array<{ value: UsageSort; label: string }> = [
  { value: "CREATED_AT_DESC", label: "최신순" },
  { value: "CREATED_AT_ASC", label: "오래된순" },
  { value: "TOKEN_USAGE_DESC", label: "토큰 많은순" },
  { value: "TOKEN_USAGE_ASC", label: "토큰 적은순" },
  { value: "CREDIT_USED_DESC", label: "크레딧 많은순" },
  { value: "CREDIT_USED_ASC", label: "크레딧 적은순" },
  { value: "MODEL_ASC", label: "모델 오름차순" },
  { value: "MODEL_DESC", label: "모델 내림차순" },
  { value: "FEATURE_TYPE_ASC", label: "기능 오름차순" },
  { value: "FEATURE_TYPE_DESC", label: "기능 내림차순" },
  { value: "STATUS_ASC", label: "상태 오름차순" },
  { value: "STATUS_DESC", label: "상태 내림차순" },
];

const LIMIT_OPTIONS = ["20", "50", "100"];

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function formatNumber(value: number | null | undefined): string {
  return new Intl.NumberFormat("ko-KR").format(value ?? 0);
}

function getFeatureLabel(value: string): string {
  return FEATURE_OPTIONS.find((option) => option.value === value)?.label ?? value;
}

function getStatusLabel(value: string): string {
  return STATUS_OPTIONS.find((option) => option.value === value)?.label ?? value;
}

function getTokenTotal(row: AdminAiUsageLogRow): number {
  return row.tokenUsage ?? (row.inputTokens ?? 0) + (row.outputTokens ?? 0);
}

function parseNonNegativeInteger(value: string, fallback: number): number {
  const numeric = Number(value);
  if (!Number.isInteger(numeric) || numeric < 0) return fallback;
  return numeric;
}

function parseOptionalPositiveInteger(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const numeric = Number(trimmed);
  return Number.isInteger(numeric) && numeric > 0 ? numeric : null;
}

function toQueryParams(filters: UsageFilters): AdminBUsageLogQueryParams {
  const limit = parseNonNegativeInteger(filters.limit, Number(DEFAULT_FILTERS.limit));
  const offset = parseNonNegativeInteger(filters.offset, 0);

  return {
    featureType: filters.featureType || null,
    status: filters.status || null,
    keyword: filters.keyword,
    applicationCaseId: parseOptionalPositiveInteger(filters.applicationCaseId),
    userId: parseOptionalPositiveInteger(filters.userId),
    model: filters.model,
    createdFrom: filters.createdFrom,
    createdTo: filters.createdTo,
    sort: filters.sort,
    limit,
    offset,
  };
}

export function AdminAiUsagePage() {
  const [rows, setRows] = useState<AdminAiUsageLogRow[]>([]);
  const [summary, setSummary] = useState<AdminBUsageSummaryResponse>(EMPTY_SUMMARY);
  const [filters, setFilters] = useState<UsageFilters>(DEFAULT_FILTERS);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const queryParams = useMemo(() => toQueryParams(filters), [filters]);
  const limit = queryParams.limit ?? Number(DEFAULT_FILTERS.limit);
  const offset = queryParams.offset ?? 0;
  const page = Math.floor(offset / Math.max(limit, 1)) + 1;
  const totalPages = Math.max(1, Math.ceil(summary.totalCount / Math.max(limit, 1)));
  const hasPrevious = offset > 0;
  const hasNext = offset + rows.length < summary.totalCount;
  const visibleFrom = summary.totalCount === 0 ? 0 : offset + 1;
  const visibleTo = Math.min(offset + rows.length, summary.totalCount);

  const load = useCallback(async (nextFilters: UsageFilters) => {
    const params = toQueryParams(nextFilters);
    setLoading(true);
    setError(null);
    try {
      const [nextRows, nextSummary] = await Promise.all([
        getAdminBUsageLogs(params),
        getAdminBUsageSummary(params),
      ]);
      setRows(nextRows);
      setSummary(nextSummary);
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 사용량 로그를 불러오지 못했습니다.");
      setRows([]);
      setSummary(EMPTY_SUMMARY);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(DEFAULT_FILTERS);
  }, [load]);

  const updateFilter = <K extends keyof UsageFilters>(key: K, value: UsageFilters[K]) => {
    setFilters((current) => ({ ...current, [key]: value }));
  };

  const handleApply = () => {
    void load(filters);
  };

  const handleReset = () => {
    setFilters(DEFAULT_FILTERS);
    void load(DEFAULT_FILTERS);
  };

  const moveToOffset = (nextOffset: number) => {
    const normalizedOffset = String(Math.max(0, nextOffset));
    const nextFilters = { ...filters, offset: normalizedOffset };
    setFilters(nextFilters);
    void load(nextFilters);
  };

  const handlePrevious = () => {
    moveToOffset(offset - limit);
  };

  const handleNext = () => {
    moveToOffset(offset + limit);
  };

  return (
    <AdminShell
      active="ai-usage"
      breadcrumb="B AI 사용량"
      title="B AI 사용량"
      icon={Gauge}
      desc="공고 분석, 기업 분석, 공고 OCR의 AI 호출 로그와 사용량을 확인합니다."
      actions={(
        <Button variant="outline" onClick={() => void load(filters)} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="space-y-5">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <SummaryCard label="전체 로그" value={formatNumber(summary.totalCount)} hint="현재 필터 기준" />
          <SummaryCard label="성공 / 실패" value={`${formatNumber(summary.successCount)} / ${formatNumber(summary.failedCount)}`} hint="상태별 호출 수" />
          <SummaryCard label="총 토큰" value={formatNumber(summary.tokenUsage)} hint="서버 집계 tokenUsage" />
          <SummaryCard label="크레딧" value={formatNumber(summary.creditUsed)} hint="사용 크레딧 합계" />
        </div>

        <Card className="border-slate-200 bg-white">
          <CardContent className="space-y-4 p-4">
            <div className="grid gap-3 xl:grid-cols-[minmax(220px,1.3fr)_160px_150px_150px_160px] xl:items-end">
              <label className="grid gap-1.5 text-sm font-semibold text-slate-700">
                <span>검색어</span>
                <div className="relative">
                  <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    value={filters.keyword}
                    onChange={(event) => updateFilter("keyword", event.target.value)}
                    placeholder="회사, 공고, 사용자, 모델, 오류"
                    className="pl-9"
                  />
                </div>
              </label>
              <SelectField label="기능" value={filters.featureType} onChange={(value) => updateFilter("featureType", value as UsageFilters["featureType"])} options={FEATURE_OPTIONS} />
              <SelectField label="상태" value={filters.status} onChange={(value) => updateFilter("status", value as UsageFilters["status"])} options={STATUS_OPTIONS} />
              <TextField label="지원 건 ID" value={filters.applicationCaseId} onChange={(value) => updateFilter("applicationCaseId", value)} inputMode="numeric" />
              <TextField label="사용자 ID" value={filters.userId} onChange={(value) => updateFilter("userId", value)} inputMode="numeric" />
            </div>

            <div className="grid gap-3 xl:grid-cols-[minmax(180px,1fr)_160px_160px_180px_120px_120px_auto] xl:items-end">
              <TextField label="모델" value={filters.model} onChange={(value) => updateFilter("model", value)} placeholder="gpt-..." />
              <TextField label="시작일" value={filters.createdFrom} onChange={(value) => updateFilter("createdFrom", value)} type="date" />
              <TextField label="종료일" value={filters.createdTo} onChange={(value) => updateFilter("createdTo", value)} type="date" />
              <SelectField label="정렬" value={filters.sort} onChange={(value) => updateFilter("sort", value as UsageSort)} options={SORT_OPTIONS} />
              <SelectField label="limit" value={filters.limit} onChange={(value) => updateFilter("limit", value)} options={LIMIT_OPTIONS.map((value) => ({ value, label: value }))} />
              <TextField label="offset" value={filters.offset} onChange={(value) => updateFilter("offset", value)} inputMode="numeric" />
              <div className="flex gap-2">
                <Button className="flex-1 bg-blue-600 text-white hover:bg-blue-700 xl:flex-none" onClick={handleApply} disabled={loading}>
                  적용
                </Button>
                <Button className="flex-1 xl:flex-none" variant="outline" onClick={handleReset} disabled={loading}>
                  <RotateCcw className="size-4" />
                  초기화
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="grid gap-3 sm:grid-cols-3">
          <FeatureCount label="공고 분석" value={summary.jobAnalysisCount} />
          <FeatureCount label="기업 분석" value={summary.companyResearchCount} />
          <FeatureCount label="공고 OCR" value={summary.jobPostingOcrCount} />
        </div>

        <Card className="border-slate-200 bg-white">
          <CardContent className="p-0">
            {loading ? (
              <div className="h-48 animate-pulse bg-slate-100" />
            ) : rows.length === 0 ? (
              <div className="p-8 text-center text-sm text-slate-500">AI 사용량 로그가 없습니다.</div>
            ) : (
              <>
                <div className="hidden overflow-x-auto lg:block">
                  <table className="w-full min-w-[1180px] text-left text-sm">
                    <thead className="border-b border-slate-200 bg-slate-50 text-xs text-slate-500">
                      <tr>
                        <th className="px-4 py-3">시간</th>
                        <th className="px-4 py-3">기능</th>
                        <th className="px-4 py-3">상태</th>
                        <th className="px-4 py-3">모델</th>
                        <th className="px-4 py-3">지원 건</th>
                        <th className="px-4 py-3">사용자</th>
                        <th className="px-4 py-3 text-right">입력</th>
                        <th className="px-4 py-3 text-right">출력</th>
                        <th className="px-4 py-3 text-right">총 토큰</th>
                        <th className="px-4 py-3 text-right">크레딧</th>
                        <th className="px-4 py-3">오류</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((row) => (
                        <tr
                          key={row.id}
                          className={`border-b border-slate-100 last:border-b-0 ${row.status === "FAILED" ? "bg-red-50/60" : ""}`}
                        >
                          <td className="px-4 py-3 text-xs text-slate-500">{formatDateTime(row.createdAt)}</td>
                          <td className="px-4 py-3 font-semibold text-slate-800">{getFeatureLabel(row.featureType)}</td>
                          <td className="px-4 py-3">
                            <StatusBadge status={row.status} />
                          </td>
                          <td className="px-4 py-3 text-slate-600">{row.model ?? "-"}</td>
                          <td className="px-4 py-3 text-slate-600">
                            <div className="max-w-[260px]">
                              <div className="truncate">{row.companyName ? `${row.companyName} · ${row.jobTitle ?? "-"}` : "-"}</div>
                              <div className="mt-1 text-xs text-slate-400">#{row.applicationCaseId ?? "-"}</div>
                            </div>
                          </td>
                          <td className="px-4 py-3 text-slate-600">
                            <div className="max-w-[220px]">
                              <div className="truncate">{row.userEmail}</div>
                              <div className="mt-1 text-xs text-slate-400">#{row.userId}</div>
                            </div>
                          </td>
                          <td className="px-4 py-3 text-right text-slate-600">{formatNumber(row.inputTokens)}</td>
                          <td className="px-4 py-3 text-right text-slate-600">{formatNumber(row.outputTokens)}</td>
                          <td className="px-4 py-3 text-right text-slate-600">{formatNumber(getTokenTotal(row))}</td>
                          <td className="px-4 py-3 text-right text-slate-600">{formatNumber(row.creditUsed)}</td>
                          <td className="px-4 py-3">
                            <ErrorSnippet message={row.errorMessage} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <div className="space-y-3 p-4 lg:hidden">
                  {rows.map((row) => (
                    <div
                      key={row.id}
                      className={`rounded-lg border p-4 ${row.status === "FAILED" ? "border-red-200 bg-red-50/70" : "border-slate-200 bg-white"}`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="text-xs text-slate-500">{formatDateTime(row.createdAt)}</div>
                          <div className="mt-1 truncate text-sm font-bold text-slate-900">{getFeatureLabel(row.featureType)}</div>
                        </div>
                        <StatusBadge status={row.status} />
                      </div>
                      <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                        <Info label="모델" value={row.model ?? "-"} />
                        <Info label="크레딧" value={formatNumber(row.creditUsed)} />
                        <Info label="입력 토큰" value={formatNumber(row.inputTokens)} />
                        <Info label="출력 토큰" value={formatNumber(row.outputTokens)} />
                        <Info label="총 토큰" value={formatNumber(getTokenTotal(row))} />
                        <Info label="사용자" value={`#${row.userId} ${row.userEmail}`} />
                      </div>
                      <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600">
                        <span className="font-semibold text-slate-500">지원 건 </span>
                        {row.companyName ? `${row.companyName} · ${row.jobTitle ?? "-"} (#${row.applicationCaseId ?? "-"})` : "-"}
                      </div>
                      {row.errorMessage && (
                        <div className="mt-3">
                          <ErrorSnippet message={row.errorMessage} />
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </>
            )}
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-white">
          <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="text-sm text-slate-600">
              {formatNumber(summary.totalCount)}건 중 {formatNumber(visibleFrom)}-{formatNumber(visibleTo)} 표시
              <span className="ml-2 text-xs text-slate-400">
                page {formatNumber(page)} / {formatNumber(totalPages)}
              </span>
            </div>
            <div className="flex gap-2">
              <Button type="button" variant="outline" onClick={handlePrevious} disabled={loading || !hasPrevious}>
                <ChevronLeft className="size-4" />
                이전
              </Button>
              <Button type="button" variant="outline" onClick={handleNext} disabled={loading || !hasNext}>
                다음
                <ChevronRight className="size-4" />
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </AdminShell>
  );
}

function SummaryCard({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <Card className="border-slate-200 bg-white">
      <CardHeader className="pb-0">
        <CardTitle className="text-sm font-semibold text-slate-500">{label}</CardTitle>
      </CardHeader>
      <CardContent className="pt-2">
        <div className="text-2xl font-bold text-slate-950">{value}</div>
        <div className="mt-1 min-h-5 truncate text-xs text-slate-500">{hint}</div>
      </CardContent>
    </Card>
  );
}

function FeatureCount({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-4 py-3">
      <div className="text-xs font-semibold text-slate-500">{label}</div>
      <div className="mt-1 text-xl font-bold text-slate-950">{formatNumber(value)}</div>
    </div>
  );
}

function SelectField({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: Array<{ value: string; label: string }>;
  onChange: (value: string) => void;
}) {
  return (
    <label className="grid gap-1.5 text-sm font-semibold text-slate-700">
      <span>{label}</span>
      <select
        className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-700 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}

function TextField({
  label,
  value,
  onChange,
  placeholder,
  type = "text",
  inputMode,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: "text" | "date";
  inputMode?: "numeric";
}) {
  return (
    <label className="grid gap-1.5 text-sm font-semibold text-slate-700">
      <span>{label}</span>
      <Input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        type={type}
        inputMode={inputMode}
      />
    </label>
  );
}

function StatusBadge({ status }: { status: string }) {
  return (
    <Badge className={status === "SUCCESS" ? "bg-emerald-100 text-emerald-700" : "border border-red-200 bg-red-100 text-red-700"}>
      {getStatusLabel(status)}
    </Badge>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-lg bg-slate-50 px-3 py-2">
      <div className="text-[11px] font-semibold text-slate-500">{label}</div>
      <div className="mt-1 truncate font-bold text-slate-900">{value}</div>
    </div>
  );
}

function ErrorSnippet({ message }: { message: string | null }) {
  if (!message) {
    return <span className="text-xs text-slate-400">-</span>;
  }

  return (
    <div title={message} className="max-w-[360px] rounded-md border border-red-200 bg-white px-2 py-1 text-xs font-medium leading-5 text-red-700">
      <p className="line-clamp-2 whitespace-pre-wrap break-words">{message}</p>
    </div>
  );
}
