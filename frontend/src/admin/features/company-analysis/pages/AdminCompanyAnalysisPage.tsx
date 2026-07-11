import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Building2,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Clock3,
  Link2,
  RefreshCw,
  Search,
  X,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/app/components/ui/collapsible";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import {
  formatAnalysisProvenanceSummary,
  parseJsonArrayOrText,
  parseJsonStringArray,
} from "@/features/applications/types/analysis";
import { VerifiedFactsList } from "@/features/applications/components/VerifiedFactsList";
import {
  getAdminCompanyAnalyses,
  getAdminCompanyAnalysisSummary,
  updateAdminCompanyAnalysisMemo,
  updateAdminCompanyAnalysisMetadata,
} from "../api";
import type {
  AdminCompanyAnalysisQueryParams,
  AdminCompanyAnalysisRow,
  AdminCompanyAnalysisSummaryResponse,
} from "../types";
import AdminShell from "../../../components/AdminShell";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";

type BooleanFilter = "all" | "true" | "false";

type MetadataForm = {
  sourceType: string;
  checkedAt: string;
  refreshRecommendedAt: string;
};

const DEFAULT_LIMIT = "50";
const DEFAULT_OFFSET = "0";
const DEFAULT_SORT = "createdAt_desc";

const EMPTY_SUMMARY: AdminCompanyAnalysisSummaryResponse = {
  totalCount: 0,
  confirmedCount: 0,
  unconfirmedCount: 0,
  refreshDueCount: 0,
  missingSourceCount: 0,
  checkedCount: 0,
  memoCount: 0,
};

const SORT_OPTIONS = [
  { value: "createdAt_desc", label: "생성일 최신순" },
  { value: "createdAt_asc", label: "생성일 오래된순" },
  { value: "checkedAt_desc", label: "확인 시각 최신순" },
  { value: "checkedAt_asc", label: "확인 시각 오래된순" },
  { value: "refreshRecommendedAt_desc", label: "갱신 권장 최신순" },
  { value: "refreshRecommendedAt_asc", label: "갱신 권장 오래된순" },
  { value: "companyName_asc", label: "회사명 오름차순" },
  { value: "companyName_desc", label: "회사명 내림차순" },
  { value: "industry_asc", label: "산업 오름차순" },
  { value: "industry_desc", label: "산업 내림차순" },
];

const COMPANY_SOURCE_TYPE_OPTIONS = [
  { value: "WEB", label: "웹 조사" },
  { value: "JOB_POSTING", label: "공고 기반" },
  { value: "MANUAL", label: "수동 입력" },
  { value: "API", label: "외부 API" },
] as const;

type CompanySourceType = (typeof COMPANY_SOURCE_TYPE_OPTIONS)[number]["value"];

const SELECT_CLASS =
  "h-9 rounded-md border border-slate-200 bg-card px-3 text-sm font-normal text-slate-900 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100";

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function isCompanySourceType(value: string | null | undefined): value is CompanySourceType {
  return COMPANY_SOURCE_TYPE_OPTIONS.some((option) => option.value === value);
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

function toDateTimeLocalValue(value: string | null | undefined): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function metadataFormFromRow(row: AdminCompanyAnalysisRow): MetadataForm {
  return {
    sourceType: row.sourceType ?? "",
    checkedAt: toDateTimeLocalValue(row.checkedAt),
    refreshRecommendedAt: toDateTimeLocalValue(row.refreshRecommendedAt),
  };
}

function hasAnalysisContent(value: string | null | undefined): boolean {
  const parsed = parseJsonArrayOrText(value);
  if (parsed.kind === "list") return parsed.items.length > 0;
  if (parsed.kind === "text") return parsed.text.trim().length > 0;
  return false;
}

function isRefreshNeeded(row: AdminCompanyAnalysisRow): boolean {
  if (!row.refreshRecommendedAt) return false;
  const date = new Date(row.refreshRecommendedAt);
  return !Number.isNaN(date.getTime()) && date.getTime() <= Date.now();
}

function isSourceMissing(row: AdminCompanyAnalysisRow): boolean {
  return !row.sourceType?.trim() || !hasAnalysisContent(row.sources);
}

function formatPostingRevision(row: AdminCompanyAnalysisRow): string {
  const analysisRevision = row.jobPostingRevision == null ? "-" : `rev ${row.jobPostingRevision}`;
  const latestRevision = row.latestJobPostingRevision == null ? "-" : `rev ${row.latestJobPostingRevision}`;
  return `분석 ${analysisRevision} / 최신 ${latestRevision}`;
}

export function AdminCompanyAnalysisPage() {
  const { canUpdate } = useAdminDomainAuthorization("AI");
  const [rows, setRows] = useState<AdminCompanyAnalysisRow[]>([]);
  const [summary, setSummary] = useState<AdminCompanyAnalysisSummaryResponse>(EMPTY_SUMMARY);
  const [memos, setMemos] = useState<Record<number, string>>({});
  const [metadataForms, setMetadataForms] = useState<Record<number, MetadataForm>>({});
  const [keyword, setKeyword] = useState("");
  const [sourceType, setSourceType] = useState("");
  const [industry, setIndustry] = useState("");
  const [confirmed, setConfirmed] = useState<BooleanFilter>("all");
  const [hasMemo, setHasMemo] = useState<BooleanFilter>("all");
  const [checked, setChecked] = useState<BooleanFilter>("all");
  const [refreshDue, setRefreshDue] = useState<BooleanFilter>("all");
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
  const [savingMemoId, setSavingMemoId] = useState<number | null>(null);
  const [savingMetadataId, setSavingMetadataId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const queryParams = useMemo<AdminCompanyAnalysisQueryParams>(
    () => ({
      keyword: blankToNull(keyword),
      sourceType: blankToNull(sourceType),
      industry: blankToNull(industry),
      confirmed: booleanFilterValue(confirmed),
      hasMemo: booleanFilterValue(hasMemo),
      checked: booleanFilterValue(checked),
      refreshDue: booleanFilterValue(refreshDue),
      applicationCaseId: positiveNumberOrNull(applicationCaseId),
      userId: positiveNumberOrNull(userId),
      createdFrom: blankToNull(createdFrom),
      createdTo: blankToNull(createdTo),
      sort,
      limit: positiveNumberOrDefault(limit, Number(DEFAULT_LIMIT)),
      offset: nonNegativeNumberOrDefault(offset, Number(DEFAULT_OFFSET)),
    }),
    [
      applicationCaseId,
      checked,
      confirmed,
      createdFrom,
      createdTo,
      hasMemo,
      industry,
      keyword,
      limit,
      offset,
      refreshDue,
      sort,
      sourceType,
      userId,
    ],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextRows, nextSummary] = await Promise.all([
        getAdminCompanyAnalyses(queryParams),
        getAdminCompanyAnalysisSummary(queryParams),
      ]);
      setRows(nextRows);
      setSummary(nextSummary);
      setMemos(Object.fromEntries(nextRows.map((row) => [row.id, row.adminMemo ?? ""])));
      setMetadataForms(Object.fromEntries(nextRows.map((row) => [row.id, metadataFormFromRow(row)])));
      setSelectedId((current) => (current && nextRows.some((row) => row.id === current) ? current : (nextRows[0]?.id ?? null)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "기업 분석 목록을 불러오지 못했습니다.");
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
    sourceType.trim() ||
    industry.trim() ||
    confirmed !== "all" ||
    hasMemo !== "all" ||
    checked !== "all" ||
    refreshDue !== "all" ||
    applicationCaseId.trim() ||
    userId.trim() ||
    createdFrom ||
    createdTo ||
    sort !== DEFAULT_SORT ||
    limit !== DEFAULT_LIMIT ||
    offset !== DEFAULT_OFFSET;

  const resetFilters = () => {
    setKeyword("");
    setSourceType("");
    setIndustry("");
    setConfirmed("all");
    setHasMemo("all");
    setChecked("all");
    setRefreshDue("all");
    setApplicationCaseId("");
    setUserId("");
    setCreatedFrom("");
    setCreatedTo("");
    setSort(DEFAULT_SORT);
    setLimit(DEFAULT_LIMIT);
    setOffset(DEFAULT_OFFSET);
  };

  const setMetadataField = (analysisId: number, field: keyof MetadataForm, value: string) => {
    setMetadataForms((current) => {
      const base = current[analysisId] ?? { sourceType: "", checkedAt: "", refreshRecommendedAt: "" };
      return {
        ...current,
        [analysisId]: {
          ...base,
          [field]: value,
        },
      };
    });
  };

  const saveMemo = async (analysisId: number) => {
    if (!canUpdate) return;
    const nextMemo = memos[analysisId] ?? "";
    setSavingMemoId(analysisId);
    setError(null);
    try {
      await updateAdminCompanyAnalysisMemo(analysisId, nextMemo);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "운영 메모를 저장하지 못했습니다.");
    } finally {
      setSavingMemoId(null);
    }
  };

  const saveMetadata = async (analysisId: number) => {
    if (!canUpdate) return;
    const metadata = metadataForms[analysisId] ?? { sourceType: "", checkedAt: "", refreshRecommendedAt: "" };
    const original = rows.find((row) => row.id === analysisId);
    const nextSourceType = blankToNull(metadata.sourceType);

    if (!nextSourceType) {
      setError("출처 유형은 비워둘 수 없습니다.");
      return;
    }
    if (!isCompanySourceType(nextSourceType)) {
      setError("출처 유형은 웹 조사, 공고 기반, 수동 입력, 외부 API 중 하나를 선택해 주세요.");
      return;
    }

    const checkedAt = blankToNull(metadata.checkedAt);
    const refreshRecommendedAt = blankToNull(metadata.refreshRecommendedAt);
    const clearCheckedAt = !checkedAt && Boolean(original?.checkedAt);
    const clearRefreshRecommendedAt = !refreshRecommendedAt && Boolean(original?.refreshRecommendedAt);

    if (
      (clearCheckedAt || clearRefreshRecommendedAt) &&
      !window.confirm("비어 있는 날짜 필드를 저장하면 기존 값이 삭제됩니다. 계속할까요?")
    ) {
      return;
    }

    setSavingMetadataId(analysisId);
    setError(null);
    try {
      await updateAdminCompanyAnalysisMetadata(analysisId, {
        sourceType: nextSourceType,
        checkedAt,
        refreshRecommendedAt,
        clearCheckedAt,
        clearRefreshRecommendedAt,
      });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "출처 메타데이터를 저장하지 못했습니다.");
    } finally {
      setSavingMetadataId(null);
    }
  };

  return (
    <AdminShell
      active="company-analysis"
      breadcrumb="기업 분석 조회"
      title="기업 분석 조회"
      icon={Building2}
      desc="지원 건별 기업 분석 결과와 출처 메타데이터, 운영 메모를 확인합니다."
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
          <SummaryCard label="전체 분석" value={summary.totalCount} icon={Building2} tone="blue" />
          <SummaryCard label="확정 완료" value={summary.confirmedCount} icon={CheckCircle2} tone="green" />
          <SummaryCard label="갱신 필요" value={summary.refreshDueCount} icon={Clock3} tone="amber" />
          <SummaryCard label="출처 누락" value={summary.missingSourceCount} icon={Link2} tone="red" />
        </div>

        <div className="rounded-lg border border-slate-200 bg-card p-4">
          <div className="grid gap-3 xl:grid-cols-[minmax(220px,1fr)_150px_150px_130px_130px_130px] xl:items-end">
            <label className="grid min-w-0 gap-1 text-xs font-semibold text-slate-500">
              검색
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <Input
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  className="bg-card pl-9 text-sm font-normal text-slate-900"
                  placeholder="회사, 공고, 사용자, 산업, 출처 검색"
                />
              </div>
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              출처 유형
              <select className={SELECT_CLASS} value={sourceType} onChange={(event) => setSourceType(event.target.value)}>
                <option value="">전체 출처</option>
                {COMPANY_SOURCE_TYPE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              산업
              <Input value={industry} onChange={(event) => setIndustry(event.target.value)} placeholder="핀테크" />
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
              확인
              <select className={SELECT_CLASS} value={checked} onChange={(event) => setChecked(event.target.value as BooleanFilter)}>
                <option value="all">전체</option>
                <option value="true">확인됨</option>
                <option value="false">미확인</option>
              </select>
            </label>
          </div>
          <div className="mt-3 grid gap-3 xl:grid-cols-[130px_120px_120px_150px_150px_190px_110px_110px_auto] xl:items-end">
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              갱신
              <select className={SELECT_CLASS} value={refreshDue} onChange={(event) => setRefreshDue(event.target.value as BooleanFilter)}>
                <option value="all">전체</option>
                <option value="true">필요</option>
                <option value="false">불필요</option>
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
          <EmptyBlock message="검색 조건에 맞는 기업 분석이 없습니다." />
        ) : (
          <>
            <div className="hidden min-w-0 gap-4 lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(320px,0.85fr)] xl:grid-cols-[minmax(0,1.05fr)_minmax(360px,0.95fr)]">
              <Card className="min-w-0 overflow-hidden rounded-lg border-slate-200 bg-card">
                <CardContent className="p-0">
                  <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
                    <div>
                      <div className="text-sm font-semibold text-slate-900">분석 목록</div>
                      <div className="text-xs text-slate-500">{rows.length.toLocaleString()}건 표시</div>
                    </div>
                  </div>
                  <div className="max-h-[720px] overflow-auto">
                    <table className="w-full min-w-[740px] text-left text-sm">
                      <thead className="sticky top-0 z-10 border-b border-slate-200 bg-slate-50 text-xs text-slate-500">
                        <tr>
                          <th className="px-4 py-3">지원 건</th>
                          <th className="px-4 py-3">산업</th>
                          <th className="px-4 py-3">출처</th>
                          <th className="px-4 py-3">상태</th>
                          <th className="px-4 py-3">갱신 권장</th>
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
                                <div className="max-w-[300px]">
                                  <div className="break-words font-semibold text-slate-900">{row.companyName}</div>
                                  <div className="mt-1 break-words text-sm text-slate-600">{row.jobTitle}</div>
                                  <div className="mt-2 text-xs text-slate-500">#{row.applicationCaseId} · {row.userEmail}</div>
                                </div>
                              </td>
                              <td className="px-4 py-3 align-top">
                                <Badge className="max-w-[180px] whitespace-normal border-slate-200 bg-slate-100 text-slate-700">
                                  {row.industry ?? "미정"}
                                </Badge>
                              </td>
                              <td className="px-4 py-3 align-top text-sm text-slate-600">
                                <div className="break-words font-semibold text-slate-800">{row.sourceType ?? "-"}</div>
                                <div className="mt-1 text-xs text-slate-500">출처 {parseJsonStringArray(row.sources).length}개</div>
                              </td>
                              <td className="px-4 py-3 align-top">
                                <QualityBadges row={row} />
                              </td>
                              <td className="px-4 py-3 align-top text-xs leading-5 text-slate-500">{formatDateTime(row.refreshRecommendedAt)}</td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </CardContent>
              </Card>

              <Card className="min-w-0 rounded-lg border-slate-200 bg-card">
                <CardContent className="p-5">
                  {selectedRow && (
                    <CompanyAnalysisDetail
                      row={selectedRow}
                      memo={memos[selectedRow.id] ?? ""}
                      metadata={metadataForms[selectedRow.id] ?? metadataFormFromRow(selectedRow)}
                      onMemoChange={(value) => setMemos((current) => ({ ...current, [selectedRow.id]: value }))}
                      onMetadataChange={(field, value) => setMetadataField(selectedRow.id, field, value)}
                      onSaveMemo={() => void saveMemo(selectedRow.id)}
                      onSaveMetadata={() => void saveMetadata(selectedRow.id)}
                      savingMemo={savingMemoId === selectedRow.id}
                      savingMetadata={savingMetadataId === selectedRow.id}
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
                        <Badge className="max-w-[140px] whitespace-normal border-slate-200 bg-slate-100 text-slate-700">
                          {row.industry ?? "미정"}
                        </Badge>
                      </div>
                      <p className="line-clamp-3 text-sm leading-6 text-slate-600">{row.companySummary ?? "요약 없음"}</p>
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
                        <CompanyAnalysisDetail
                          row={row}
                          memo={memos[row.id] ?? ""}
                          metadata={metadataForms[row.id] ?? metadataFormFromRow(row)}
                          onMemoChange={(value) => setMemos((current) => ({ ...current, [row.id]: value }))}
                          onMetadataChange={(field, value) => setMetadataField(row.id, field, value)}
                          onSaveMemo={() => void saveMemo(row.id)}
                          onSaveMetadata={() => void saveMetadata(row.id)}
                          savingMemo={savingMemoId === row.id}
                          savingMetadata={savingMetadataId === row.id}
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
  icon: typeof Building2;
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
    <div className="grid min-w-0 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(320px,0.85fr)] xl:grid-cols-[minmax(0,1.05fr)_minmax(360px,0.95fr)]">
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

function QualityBadges({ row, horizontal = false }: { row: AdminCompanyAnalysisRow; horizontal?: boolean }) {
  return (
    <div className={`flex ${horizontal ? "flex-row flex-wrap" : "flex-col"} items-start gap-1.5`}>
      {row.confirmedAt ? (
        <Badge className="border-emerald-200 bg-emerald-50 text-emerald-700">확정</Badge>
      ) : (
        <Badge className="border-amber-200 bg-amber-50 text-amber-700">미확정</Badge>
      )}
      <StalePostingBadge row={row} />
      {isRefreshNeeded(row) && <Badge className="border-orange-200 bg-orange-50 text-orange-700">갱신 필요</Badge>}
      {isSourceMissing(row) && <Badge className="border-red-200 bg-red-50 text-red-700">출처 누락</Badge>}
    </div>
  );
}

function StalePostingBadge({ row }: { row: AdminCompanyAnalysisRow }) {
  if (!row.staleAgainstLatestPosting) return null;
  return (
    <Badge className="border-rose-200 bg-rose-50 text-rose-700" title={formatPostingRevision(row)}>
      공고 변경됨
    </Badge>
  );
}

function CompanyAnalysisDetail({
  row,
  memo,
  metadata,
  onMemoChange,
  onMetadataChange,
  onSaveMemo,
  onSaveMetadata,
  savingMemo,
  savingMetadata,
  canUpdate,
  compact = false,
}: {
  row: AdminCompanyAnalysisRow;
  memo: string;
  metadata: MetadataForm;
  onMemoChange: (value: string) => void;
  onMetadataChange: (field: keyof MetadataForm, value: string) => void;
  onSaveMemo: () => void;
  onSaveMetadata: () => void;
  savingMemo: boolean;
  savingMetadata: boolean;
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
          <Badge className="max-w-[180px] whitespace-normal border-slate-200 bg-slate-100 text-slate-700">{row.industry ?? "미정"}</Badge>
        </div>
      )}

      <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
        <div className="text-xs font-semibold text-slate-500">기업 요약</div>
        <p className="mt-2 whitespace-pre-line break-words text-sm leading-6 text-slate-700">{row.companySummary ?? "요약 없음"}</p>
      </div>

      <div className="grid gap-2 sm:grid-cols-2">
        <MetaBlock label="출처 유형" value={row.sourceType ?? "미정"} />
        <MetaBlock label="확인 시각" value={formatDateTime(row.checkedAt)} />
        <MetaBlock label="갱신 권장" value={formatDateTime(row.refreshRecommendedAt)} />
        <MetaBlock label="공고 revision" value={formatPostingRevision(row)} />
        <MetaBlock label="확정일" value={row.confirmedAt ? formatDateTime(row.confirmedAt) : "미확정"} />
        <MetaBlock label="생성 모델" value={formatAnalysisProvenanceSummary(row)} />
      </div>

      <TagBlock title="경쟁사" items={parseJsonStringArray(row.competitors)} emptyLabel="경쟁사 미정" />

      {canUpdate && <MetadataEditor
        key={row.id}
        metadata={metadata}
        onMetadataChange={onMetadataChange}
        onSaveMetadata={onSaveMetadata}
        saving={savingMetadata}
      />}

      <div className="grid gap-3">
        <TextBlock title="최근 이슈" value={row.recentIssues} />
        <div className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 p-3">
          <div className="text-xs font-semibold text-slate-500">검증된 사실</div>
          <VerifiedFactsList value={row.verifiedFacts} />
        </div>
        <TextBlock title="AI 추론" value={row.aiInferences} />
        <TextBlock title="면접 포인트" value={row.interviewPoints} />
      </div>

      <TagBlock title="출처" items={parseJsonStringArray(row.sources)} emptyLabel="출처 없음" />

      {canUpdate ? <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
        <div className="text-xs font-semibold text-slate-500">운영 메모</div>
        <Textarea
          value={memo}
          onChange={(event) => onMemoChange(event.target.value)}
          className="min-h-24 bg-card"
          placeholder="분석 오류, 출처 검증, 사용자 문의 대응을 기록"
        />
        <div className="flex justify-end">
          <Button type="button" size="sm" variant="outline" onClick={onSaveMemo} disabled={savingMemo}>
            {savingMemo && <RefreshCw className="size-4 animate-spin" />}
            메모 저장
          </Button>
        </div>
      </div> : <TextBlock title="운영 메모" value={row.adminMemo} />}
    </div>
  );
}

function MetadataEditor({
  metadata,
  onMetadataChange,
  onSaveMetadata,
  saving,
}: {
  metadata: MetadataForm;
  onMetadataChange: (field: keyof MetadataForm, value: string) => void;
  onSaveMetadata: () => void;
  saving: boolean;
}) {
  const [open, setOpen] = useState(false);

  return (
    <Collapsible open={open} onOpenChange={setOpen} className="rounded-lg border border-slate-200 bg-slate-50">
      <CollapsibleTrigger asChild>
        <button
          type="button"
          className="flex w-full items-center justify-between gap-3 px-3 py-3 text-left text-xs font-semibold text-slate-500"
        >
          <span>출처 메타데이터 편집</span>
          <span className="ml-auto text-[11px] text-slate-400">기본 접힘</span>
          {open ? <ChevronUp className="size-4 text-slate-400" /> : <ChevronDown className="size-4 text-slate-400" />}
        </button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="space-y-3 border-t border-slate-200 p-3">
          <div className="grid gap-2 md:grid-cols-3">
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              출처 유형
              <select
                value={isCompanySourceType(metadata.sourceType) ? metadata.sourceType : ""}
                onChange={(event) => onMetadataChange("sourceType", event.target.value)}
                className={SELECT_CLASS}
              >
                <option value="" disabled>
                  출처 선택
                </option>
                {COMPANY_SOURCE_TYPE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              확인 시각
              <Input
                type="datetime-local"
                value={metadata.checkedAt}
                onChange={(event) => onMetadataChange("checkedAt", event.target.value)}
                className="bg-card text-sm font-normal text-slate-900"
              />
            </label>
            <label className="grid gap-1 text-xs font-semibold text-slate-500">
              갱신 권장
              <Input
                type="datetime-local"
                value={metadata.refreshRecommendedAt}
                onChange={(event) => onMetadataChange("refreshRecommendedAt", event.target.value)}
                className="bg-card text-sm font-normal text-slate-900"
              />
            </label>
          </div>
          <div className="flex justify-end">
            <Button type="button" size="sm" variant="outline" onClick={onSaveMetadata} disabled={saving}>
              {saving && <RefreshCw className="size-4 animate-spin" />}
              메타데이터 저장
            </Button>
          </div>
        </div>
      </CollapsibleContent>
    </Collapsible>
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

function TagBlock({ title, items, emptyLabel }: { title: string; items: string[]; emptyLabel: string }) {
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
          <span className="text-sm text-slate-400">{emptyLabel}</span>
        )}
      </div>
    </div>
  );
}
