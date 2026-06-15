import { useEffect, useMemo, useRef, useState } from "react";
import type React from "react";
import {
  AlertTriangle,
  BarChart3,
  Briefcase,
  Building2,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  FileWarning,
  FileText,
  History,
  Info as InfoIcon,
  Loader2,
  RefreshCw,
  Search,
  type LucideIcon,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Checkbox } from "@/app/components/ui/checkbox";
import { Input } from "@/app/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/app/components/ui/tabs";
import { Textarea } from "@/app/components/ui/textarea";
import {
  APPLICATION_SOURCE_OPTIONS,
  APPLICATION_STATUS_OPTIONS,
  type ApplicationSourceType,
  type ApplicationStatus,
  getApplicationStatusLabel,
} from "@/features/applications/types/applicationCase";
import { parseJsonArrayOrText, parseJsonStringArray } from "@/features/applications/types/analysis";
import {
  getAdminApplicationCaseDetail,
  getAdminApplicationCases,
  getAdminApplicationCaseSummary,
  updateAdminApplicationCaseStatus,
} from "../api";
import type {
  AdminApplicationCaseDetail,
  AdminApplicationCaseQueryParams,
  AdminApplicationCaseRow,
  AdminApplicationCaseSummaryResponse,
} from "../types";
import AdminShell from "../../../components/AdminShell";

const DETAIL_TABS = [
  { value: "overview", label: "개요", icon: InfoIcon },
  { value: "posting", label: "공고문", icon: FileText },
  { value: "jobAnalysis", label: "공고 분석", icon: BarChart3 },
  { value: "companyAnalysis", label: "기업 분석", icon: Building2 },
  { value: "aiLogs", label: "AI 로그", icon: History },
] as const;

type DetailTab = (typeof DETAIL_TABS)[number]["value"];
type FavoriteFilter = "" | "true" | "false";
type LoadOverrides = {
  limit?: number;
  offset?: number;
};

const SOURCE_TYPE_LABELS: Record<ApplicationSourceType, string> = {
  TEXT: "텍스트",
  PDF: "PDF",
  IMAGE: "이미지",
  URL: "URL",
  MANUAL: "수동 입력",
};

const ANALYSIS_STATE_OPTIONS = [
  { value: "", label: "전체 분석 상태" },
  { value: "NO_ANALYSIS", label: "분석 없음" },
  { value: "MISSING_JOB_ANALYSIS", label: "공고 분석 없음" },
  { value: "MISSING_COMPANY_ANALYSIS", label: "기업 분석 없음" },
  { value: "MISSING_ANY_ANALYSIS", label: "분석 누락" },
  { value: "COMPLETE_ANALYSIS", label: "분석 완료" },
] as const;

const SORT_OPTIONS = [
  { value: "updatedAt_desc", label: "최근 수정순" },
  { value: "updatedAt_asc", label: "오래된 수정순" },
  { value: "createdAt_desc", label: "최근 생성순" },
  { value: "createdAt_asc", label: "오래된 생성순" },
  { value: "deadlineDate_asc", label: "마감 임박순" },
  { value: "deadlineDate_desc", label: "마감 먼 순" },
  { value: "companyName_asc", label: "기업명 오름차순" },
  { value: "companyName_desc", label: "기업명 내림차순" },
  { value: "latestJobAnalysisAt_desc", label: "공고 분석 최신순" },
  { value: "latestCompanyAnalysisAt_desc", label: "기업 분석 최신순" },
] as const;

const PAGE_SIZE_OPTIONS = [20, 50, 100, 200] as const;
const SELECT_CLASS_NAME =
  "h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100";

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function formatDate(value: string | null | undefined): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium" }).format(new Date(value));
}

function formatTextValue(value: unknown): string {
  if (value == null) return "";
  if (typeof value === "string") return value.trim();
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  if (Array.isArray(value)) return value.map(formatTextValue).filter(Boolean).join("\n");

  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function formatNumber(value: number | null | undefined): string {
  return value == null ? "-" : value.toLocaleString();
}

function parseStringArrayValue(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(formatTextValue).filter(Boolean);
  return parseJsonStringArray(formatTextValue(value));
}

export function AdminApplicationCasesPage() {
  const [rows, setRows] = useState<AdminApplicationCaseRow[]>([]);
  const [summary, setSummary] = useState<AdminApplicationCaseSummaryResponse | null>(null);
  const [detail, setDetail] = useState<AdminApplicationCaseDetail | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<ApplicationStatus | "">("");
  const [includeArchived, setIncludeArchived] = useState(true);
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [sourceType, setSourceType] = useState<ApplicationSourceType | "">("");
  const [favorite, setFavorite] = useState<FavoriteFilter>("");
  const [createdFrom, setCreatedFrom] = useState("");
  const [createdTo, setCreatedTo] = useState("");
  const [deadlineFrom, setDeadlineFrom] = useState("");
  const [deadlineTo, setDeadlineTo] = useState("");
  const [analysisState, setAnalysisState] = useState("");
  const [sort, setSort] = useState("updatedAt_desc");
  const [limit, setLimit] = useState(50);
  const [offset, setOffset] = useState(0);
  const [memo, setMemo] = useState("");
  const [activeTab, setActiveTab] = useState<DetailTab>("overview");
  const [rowsLoading, setRowsLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [savingStatus, setSavingStatus] = useState<ApplicationStatus | null>(null);
  const selectedIdRef = useRef<number | null>(null);
  const detailRequestRef = useRef(0);

  useEffect(() => {
    selectedIdRef.current = selectedId;
  }, [selectedId]);

  const selected = useMemo(() => {
    if (selectedId === null) return null;
    return rows.find((row) => row.id === selectedId) ?? null;
  }, [rows, selectedId]);

  const resetOffset = () => setOffset(0);

  const buildQueryParams = (overrides: LoadOverrides = {}): AdminApplicationCaseQueryParams => {
    const nextLimit = overrides.limit ?? limit;
    const nextOffset = overrides.offset ?? offset;

    return {
      keyword: keyword.trim() || undefined,
      status: status || undefined,
      includeArchived,
      includeDeleted,
      sourceType: sourceType || undefined,
      favorite: favorite === "" ? undefined : favorite === "true",
      createdFrom: createdFrom || undefined,
      createdTo: createdTo || undefined,
      deadlineFrom: deadlineFrom || undefined,
      deadlineTo: deadlineTo || undefined,
      analysisState: analysisState || undefined,
      sort: sort || undefined,
      limit: nextLimit,
      offset: nextOffset,
    };
  };

  const loadRows = async (overrides: LoadOverrides = {}) => {
    setRowsLoading(true);
    setListError(null);
    try {
      const params = buildQueryParams(overrides);
      const [nextRows, nextSummary] = await Promise.all([
        getAdminApplicationCases(params),
        getAdminApplicationCaseSummary(params),
      ]);
      const currentSelectedId = selectedIdRef.current;
      const nextSelectedId =
        currentSelectedId !== null && nextRows.some((row) => row.id === currentSelectedId)
          ? currentSelectedId
          : nextRows[0]?.id ?? null;

      selectedIdRef.current = nextSelectedId;
      setRows(nextRows);
      setSummary(nextSummary);
      setSelectedId(nextSelectedId);
      if (nextSelectedId === null || nextSelectedId !== currentSelectedId) {
        setDetail(null);
      }
    } catch (err) {
      setListError(err instanceof Error ? err.message : "지원 건 목록을 불러오지 못했습니다.");
    } finally {
      setRowsLoading(false);
    }
  };

  const applyFilters = () => {
    setOffset(0);
    void loadRows({ offset: 0 });
  };

  const handlePage = (nextOffset: number) => {
    const normalizedOffset = Math.max(nextOffset, 0);
    setOffset(normalizedOffset);
    void loadRows({ offset: normalizedOffset });
  };

  const handleLimitChange = (nextLimit: number) => {
    setLimit(nextLimit);
    setOffset(0);
    void loadRows({ limit: nextLimit, offset: 0 });
  };

  const loadDetail = async (id: number) => {
    const requestId = detailRequestRef.current + 1;
    detailRequestRef.current = requestId;
    setDetailLoading(true);
    setDetailError(null);
    try {
      const nextDetail = await getAdminApplicationCaseDetail(id);
      if (selectedIdRef.current === id && detailRequestRef.current === requestId) {
        setDetail(nextDetail);
      }
    } catch (err) {
      if (selectedIdRef.current === id && detailRequestRef.current === requestId) {
        setDetailError(err instanceof Error ? err.message : "지원 건 상세를 불러오지 못했습니다.");
      }
    } finally {
      if (selectedIdRef.current === id && detailRequestRef.current === requestId) {
        setDetailLoading(false);
      }
    }
  };

  useEffect(() => {
    void loadRows();
  }, []);

  useEffect(() => {
    if (selectedId !== null) {
      setDetail(null);
      void loadDetail(selectedId);
      return;
    }

    setDetail(null);
    setDetailLoading(false);
    setDetailError(null);
  }, [selectedId]);

  const handleStatus = async (nextStatus: ApplicationStatus) => {
    if (!selected) return;

    const currentStatus = detail?.applicationCase.status ?? selected.status;
    if (savingStatus || nextStatus === currentStatus) return;

    setSavingStatus(nextStatus);
    setStatusError(null);
    try {
      const updated = await updateAdminApplicationCaseStatus(selected.id, nextStatus, memo);
      setRows((items) => items.map((item) => (item.id === updated.id ? updated : item)));
      setDetail((current) =>
        current?.applicationCase.id === updated.id
          ? { ...current, applicationCase: updated }
          : current,
      );
      setMemo("");
    } catch (err) {
      setStatusError(err instanceof Error ? err.message : "상태를 변경하지 못했습니다.");
    } finally {
      setSavingStatus(null);
    }
  };

  const handleSelect = (id: number) => {
    if (id === selectedId) return;
    selectedIdRef.current = id;
    setSelectedId(id);
    setDetail(null);
    setDetailError(null);
  };

  return (
    <AdminShell
      active="application-cases"
      breadcrumb="지원 건 관리"
      title="지원 건 관리"
      icon={Briefcase}
      desc="지원 건별 상태, 공고 revision, 분석 이력과 B AI 사용 로그를 확인합니다."
      actions={(
        <Button variant="outline" onClick={() => void loadRows()} disabled={rowsLoading}>
          <RefreshCw className={`size-4 ${rowsLoading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="space-y-5">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <SummaryCard
            label="전체 지원"
            value={formatNumber(summary?.totalCount)}
            hint={`초안 ${formatNumber(summary?.draftCount)} · 분석중 ${formatNumber(summary?.analyzingCount)}`}
            icon={Briefcase}
            tone="blue"
          />
          <SummaryCard
            label="준비 / 지원"
            value={`${formatNumber(summary?.readyCount)} / ${formatNumber(summary?.appliedCount)}`}
            hint={`마감 ${formatNumber(summary?.closedCount)}`}
            icon={CheckCircle2}
            tone="green"
          />
          <SummaryCard
            label="분석 누락"
            value={formatNumber(summary?.missingAnyAnalysisCount)}
            hint={`공고 ${formatNumber(summary?.missingJobAnalysisCount)} · 기업 ${formatNumber(summary?.missingCompanyAnalysisCount)}`}
            icon={AlertTriangle}
            tone="amber"
          />
          <SummaryCard
            label="분석 완료 / 실패"
            value={`${formatNumber(summary?.completeAnalysisCount)} / ${formatNumber(summary?.failedUsageCount)}`}
            hint="분석 완료 / AI 실패 로그"
            icon={FileWarning}
            tone="red"
          />
        </div>

        <div className="grid gap-5 lg:grid-cols-[360px_minmax(0,1fr)]">
        <section className="min-w-0 space-y-4">
          <Card className="border-slate-200 bg-white">
            <CardContent className="space-y-4 p-4">
              <label className="grid gap-1 text-xs font-semibold text-slate-500">
                검색
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    value={keyword}
                    onChange={(event) => {
                      setKeyword(event.target.value);
                      resetOffset();
                    }}
                    placeholder="기업, 직무, 이메일 검색"
                    className="pl-9"
                  />
                </div>
              </label>

              <div className="space-y-2">
                <div className="text-xs font-semibold text-slate-500">상태</div>
                <div className="flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    variant={status === "" ? "default" : "outline"}
                    onClick={() => {
                      setStatus("");
                      resetOffset();
                    }}
                  >
                    전체
                  </Button>
                  {APPLICATION_STATUS_OPTIONS.map((option) => (
                    <Button
                      key={option.value}
                      size="sm"
                      variant={status === option.value ? "default" : "outline"}
                      onClick={() => {
                        setStatus(option.value);
                        resetOffset();
                      }}
                    >
                      {option.label}
                    </Button>
                  ))}
                </div>
              </div>

              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                <label className="grid gap-1 text-xs font-semibold text-slate-500">
                  출처 유형
                  <select
                    className={SELECT_CLASS_NAME}
                    value={sourceType}
                    onChange={(event) => {
                      setSourceType(event.target.value as ApplicationSourceType | "");
                      resetOffset();
                    }}
                  >
                    <option value="">전체 출처</option>
                    {APPLICATION_SOURCE_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {SOURCE_TYPE_LABELS[option.value] ?? option.value}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="grid gap-1 text-xs font-semibold text-slate-500">
                  즐겨찾기
                  <select
                    className={SELECT_CLASS_NAME}
                    value={favorite}
                    onChange={(event) => {
                      setFavorite(event.target.value as FavoriteFilter);
                      resetOffset();
                    }}
                  >
                    <option value="">전체</option>
                    <option value="true">즐겨찾기만</option>
                    <option value="false">즐겨찾기 제외</option>
                  </select>
                </label>
              </div>

              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                <label className="grid gap-1 text-xs font-semibold text-slate-500">
                  생성 시작
                  <Input
                    type="date"
                    value={createdFrom}
                    onChange={(event) => {
                      setCreatedFrom(event.target.value);
                      resetOffset();
                    }}
                    className="bg-white"
                  />
                </label>
                <label className="grid gap-1 text-xs font-semibold text-slate-500">
                  생성 종료
                  <Input
                    type="date"
                    value={createdTo}
                    onChange={(event) => {
                      setCreatedTo(event.target.value);
                      resetOffset();
                    }}
                    className="bg-white"
                  />
                </label>
              </div>

              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                <label className="grid gap-1 text-xs font-semibold text-slate-500">
                  마감 시작
                  <Input
                    type="date"
                    value={deadlineFrom}
                    onChange={(event) => {
                      setDeadlineFrom(event.target.value);
                      resetOffset();
                    }}
                    className="bg-white"
                  />
                </label>
                <label className="grid gap-1 text-xs font-semibold text-slate-500">
                  마감 종료
                  <Input
                    type="date"
                    value={deadlineTo}
                    onChange={(event) => {
                      setDeadlineTo(event.target.value);
                      resetOffset();
                    }}
                    className="bg-white"
                  />
                </label>
              </div>

              <label className="grid gap-1 text-xs font-semibold text-slate-500">
                분석 상태
                <select
                  className={SELECT_CLASS_NAME}
                  value={analysisState}
                  onChange={(event) => {
                    setAnalysisState(event.target.value);
                    resetOffset();
                  }}
                >
                  {ANALYSIS_STATE_OPTIONS.map((option) => (
                    <option key={option.value || "all"} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>

              <label className="grid gap-1 text-xs font-semibold text-slate-500">
                정렬
                <select
                  className={SELECT_CLASS_NAME}
                  value={sort}
                  onChange={(event) => {
                    setSort(event.target.value);
                    resetOffset();
                  }}
                >
                  {SORT_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>

              <div className="grid gap-2">
                <label className="flex items-center gap-2 text-xs font-semibold text-slate-600">
                  <Checkbox
                    checked={includeArchived}
                    onCheckedChange={(checked) => {
                      setIncludeArchived(Boolean(checked));
                      resetOffset();
                    }}
                  />
                  보관 포함
                </label>
                <label className="flex items-center gap-2 text-xs font-semibold text-slate-600">
                  <Checkbox
                    checked={includeDeleted}
                    onCheckedChange={(checked) => {
                      setIncludeDeleted(Boolean(checked));
                      resetOffset();
                    }}
                  />
                  삭제 포함
                </label>
              </div>

              <Button className="w-full bg-blue-600 text-white hover:bg-blue-700" onClick={applyFilters} disabled={rowsLoading}>
                {rowsLoading && <Loader2 className="size-4 animate-spin" />}
                필터 적용
              </Button>
            </CardContent>
          </Card>

          {listError && <ErrorBox message={listError} />}

          <Card className="border-slate-200 bg-white">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-bold text-slate-900">지원 건 목록</CardTitle>
            </CardHeader>
            <CardContent className="p-3 pt-0">
              {rowsLoading ? (
                <ListLoadingState />
              ) : rows.length === 0 ? (
                <EmptyState title="조건에 맞는 지원 건이 없습니다." description="검색어와 포함 조건을 조정해 다시 조회하세요." />
              ) : (
                <div className="space-y-2 lg:max-h-[calc(100vh-330px)] lg:overflow-y-auto lg:pr-1">
                  {rows.map((row) => (
                    <button
                      key={row.id}
                      type="button"
                      className={`w-full rounded-lg border bg-white p-3 text-left transition-colors ${
                        selected?.id === row.id ? "border-blue-300 ring-2 ring-blue-100" : "border-slate-200 hover:border-blue-200"
                      }`}
                      onClick={() => handleSelect(row.id)}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <div className="truncate text-sm font-bold text-slate-950">{row.companyName} · {row.jobTitle}</div>
                          <div className="truncate text-xs text-slate-500">{row.userEmail}</div>
                        </div>
                        <Badge variant="outline">{getApplicationStatusLabel(row.status)}</Badge>
                      </div>
                      <div className="mt-2 flex flex-wrap gap-1.5 text-[11px] text-slate-500">
                        <span>공고 rev {row.latestPostingRevision ?? "-"}</span>
                        <span>마감 {formatDate(row.deadlineDate)}</span>
                        {row.archivedAt && <span>보관</span>}
                        {row.deletedAt && <span>삭제</span>}
                      </div>
                    </button>
                  ))}
                </div>
              )}
              <PaginationControls
                limit={limit}
                offset={offset}
                totalCount={summary?.totalCount ?? null}
                rowsLength={rows.length}
                loading={rowsLoading}
                onLimitChange={handleLimitChange}
                onPage={handlePage}
              />
            </CardContent>
          </Card>
        </section>

        <section className="min-w-0 space-y-4">
          {selectedId === null ? (
            <Card className="border-slate-200 bg-white">
              <CardContent className="p-8">
                <EmptyState title="지원 건을 선택하세요." description="왼쪽 목록에서 지원 건을 선택하면 상세 정보와 분석 이력을 확인할 수 있습니다." />
              </CardContent>
            </Card>
          ) : detailLoading && !detail ? (
            <DetailLoadingState />
          ) : detail ? (
            <DetailPanel
              detail={detail}
              activeTab={activeTab}
              onTabChange={(value) => setActiveTab(value as DetailTab)}
              memo={memo}
              onMemoChange={setMemo}
              savingStatus={savingStatus}
              statusError={statusError}
              onStatus={handleStatus}
            />
          ) : (
            <Card className="border-slate-200 bg-white">
              <CardContent className="p-8">
                {detailError ? (
                  <EmptyState title="상세 정보를 불러오지 못했습니다." description={detailError} />
                ) : (
                  <EmptyState title="상세 정보가 없습니다." description="목록에서 다른 지원 건을 선택하거나 새로고침하세요." />
                )}
              </CardContent>
            </Card>
          )}
        </section>
      </div>
      </div>
    </AdminShell>
  );
}

function SummaryCard({
  label,
  value,
  hint,
  icon: Icon,
  tone,
}: {
  label: string;
  value: string;
  hint: string;
  icon: LucideIcon;
  tone: "blue" | "green" | "amber" | "red";
}) {
  const toneClass = {
    blue: "bg-blue-50 text-blue-700",
    green: "bg-emerald-50 text-emerald-700",
    amber: "bg-amber-50 text-amber-700",
    red: "bg-red-50 text-red-700",
  }[tone];

  return (
    <Card className="border-slate-200 bg-white">
      <CardContent className="flex items-center justify-between gap-3 p-4">
        <div className="min-w-0">
          <div className="text-xs font-semibold text-slate-500">{label}</div>
          <div className="mt-1 truncate text-2xl font-bold text-slate-950">{value}</div>
          <div className="mt-1 truncate text-xs text-slate-500">{hint}</div>
        </div>
        <div className={`flex size-10 shrink-0 items-center justify-center rounded-lg ${toneClass}`}>
          <Icon className="size-5" />
        </div>
      </CardContent>
    </Card>
  );
}

function PaginationControls({
  limit,
  offset,
  totalCount,
  rowsLength,
  loading,
  onLimitChange,
  onPage,
}: {
  limit: number;
  offset: number;
  totalCount: number | null;
  rowsLength: number;
  loading: boolean;
  onLimitChange: (limit: number) => void;
  onPage: (offset: number) => void;
}) {
  const hasRows = rowsLength > 0;
  const start = hasRows ? offset + 1 : 0;
  const end = hasRows ? offset + rowsLength : 0;
  const currentPage = Math.floor(offset / limit) + 1;
  const totalPages = totalCount == null ? null : Math.max(1, Math.ceil(totalCount / limit));
  const hasPrevious = offset > 0;
  const hasNext = totalCount == null ? rowsLength === limit : offset + limit < totalCount;

  return (
    <div className="mt-3 space-y-3 border-t border-slate-100 pt-3">
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
        <span>
          {start.toLocaleString()}-{end.toLocaleString()} / {totalCount == null ? "-" : totalCount.toLocaleString()}
        </span>
        <span>
          {currentPage.toLocaleString()}
          {totalPages ? ` / ${totalPages.toLocaleString()}` : ""}
        </span>
      </div>
      <div className="grid gap-2 sm:grid-cols-[1fr_auto_auto]">
        <select
          className={SELECT_CLASS_NAME}
          value={limit}
          disabled={loading}
          onChange={(event) => onLimitChange(Number(event.target.value))}
        >
          {PAGE_SIZE_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option}개씩
            </option>
          ))}
        </select>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => onPage(offset - limit)}
          disabled={loading || !hasPrevious}
        >
          이전
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => onPage(offset + limit)}
          disabled={loading || !hasNext}
        >
          다음
        </Button>
      </div>
    </div>
  );
}

function DetailPanel({
  detail,
  activeTab,
  onTabChange,
  memo,
  onMemoChange,
  savingStatus,
  statusError,
  onStatus,
}: {
  detail: AdminApplicationCaseDetail;
  activeTab: DetailTab;
  onTabChange: (value: string) => void;
  memo: string;
  onMemoChange: (value: string) => void;
  savingStatus: ApplicationStatus | null;
  statusError: string | null;
  onStatus: (nextStatus: ApplicationStatus) => void;
}) {
  return (
    <Tabs value={activeTab} onValueChange={onTabChange} className="space-y-4">
      <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-white p-1">
        {DETAIL_TABS.map((tab) => (
          <TabsTrigger key={tab.value} value={tab.value} className="shrink-0 px-3 py-2">
            <tab.icon className="size-4" />
            {tab.label}
          </TabsTrigger>
        ))}
      </TabsList>

      <TabsContent value="overview" className="mt-0 space-y-4">
        <OverviewTab
          detail={detail}
          memo={memo}
          onMemoChange={onMemoChange}
          savingStatus={savingStatus}
          statusError={statusError}
          onStatus={onStatus}
        />
      </TabsContent>
      <TabsContent value="posting" className="mt-0">
        <PostingTab detail={detail} />
      </TabsContent>
      <TabsContent value="jobAnalysis" className="mt-0">
        <JobAnalysisTab detail={detail} />
      </TabsContent>
      <TabsContent value="companyAnalysis" className="mt-0">
        <CompanyAnalysisTab detail={detail} />
      </TabsContent>
      <TabsContent value="aiLogs" className="mt-0">
        <AiLogsTab detail={detail} />
      </TabsContent>
    </Tabs>
  );
}

function OverviewTab({
  detail,
  memo,
  onMemoChange,
  savingStatus,
  statusError,
  onStatus,
}: {
  detail: AdminApplicationCaseDetail;
  memo: string;
  onMemoChange: (value: string) => void;
  savingStatus: ApplicationStatus | null;
  statusError: string | null;
  onStatus: (nextStatus: ApplicationStatus) => void;
}) {
  const currentStatus = detail.applicationCase.status;
  const isSaving = savingStatus !== null;

  return (
    <>
      <Card className="border-slate-200 bg-white">
        <CardHeader>
          <CardTitle className="text-lg font-bold text-slate-950">
            {detail.applicationCase.companyName} · {detail.applicationCase.jobTitle}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
            <Info label="사용자" value={detail.applicationCase.userEmail} />
            <Info label="상태" value={getApplicationStatusLabel(currentStatus)} />
            <Info label="마감일" value={formatDate(detail.applicationCase.deadlineDate)} />
            <Info label="생성" value={formatDateTime(detail.applicationCase.createdAt)} />
            <Info label="수정" value={formatDateTime(detail.applicationCase.updatedAt)} />
          </div>

          <div className="space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="text-sm font-semibold text-slate-900">상태 변경</div>
              {isSaving && (
                <div className="inline-flex items-center gap-1.5 text-xs font-semibold text-blue-700">
                  <Loader2 className="size-3.5 animate-spin" />
                  메모 저장 중
                </div>
              )}
            </div>
            <div className="flex flex-wrap gap-2">
              {APPLICATION_STATUS_OPTIONS.map((option) => {
                const isCurrent = option.value === currentStatus;
                const isThisSaving = savingStatus === option.value;

                return (
                  <Button
                    key={option.value}
                    size="sm"
                    variant={isCurrent ? "default" : "outline"}
                    disabled={isSaving || isCurrent}
                    aria-current={isCurrent ? "true" : undefined}
                    onClick={() => onStatus(option.value)}
                  >
                    {isThisSaving && <Loader2 className="size-3.5 animate-spin" />}
                    {option.label}
                    {isCurrent && <span className="text-xs opacity-80">현재</span>}
                  </Button>
                );
              })}
            </div>
            <Textarea
              value={memo}
              onChange={(event) => onMemoChange(event.target.value)}
              className="min-h-20 bg-white"
              placeholder="상태 변경 메모"
              disabled={isSaving}
            />
            <div className="text-xs text-slate-500">
              상태를 변경하면 입력한 메모가 함께 저장됩니다.
            </div>
            {statusError && <ErrorBox message={statusError} />}
          </div>
        </CardContent>
      </Card>

      <GridSection title="연결 데이터" empty={false}>
        <Info label="공고문 revision" value={`${detail.jobPostings.length}개`} />
        <Info label="공고 분석" value={`${detail.jobAnalyses.length}개`} />
        <Info label="기업 분석" value={`${detail.companyAnalyses.length}개`} />
        <Info label="AI 로그" value={`${detail.usageLogs.length}개`} />
      </GridSection>
    </>
  );
}

function PostingTab({ detail }: { detail: AdminApplicationCaseDetail }) {
  return (
    <GridSection title="공고문 revision" empty={detail.jobPostings.length === 0} emptyMessage="등록된 공고문 revision이 없습니다.">
      {detail.jobPostings.map((posting) => (
        <Card key={posting.id} className="border-slate-200 bg-white">
          <CardContent className="space-y-3 p-4 text-sm">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="font-semibold text-slate-900">rev {posting.revision} · {posting.sourceType}</div>
              <div className="text-xs text-slate-500">{formatDateTime(posting.createdAt)}</div>
            </div>
            <ExpandableText
              value={posting.extractedText ?? posting.originalText ?? posting.uploadedFileUrl}
              emptyText="공고 원문이 없습니다."
              collapsedLines={6}
            />
          </CardContent>
        </Card>
      ))}
    </GridSection>
  );
}

function JobAnalysisTab({ detail }: { detail: AdminApplicationCaseDetail }) {
  return (
    <GridSection title="공고 분석 이력" empty={detail.jobAnalyses.length === 0} emptyMessage="공고 분석 이력이 없습니다.">
      {detail.jobAnalyses.map((analysis) => (
        <Card key={analysis.id} className="border-slate-200 bg-white">
          <CardContent className="space-y-3 p-4 text-sm">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="font-semibold text-slate-900">#{analysis.id} · 공고 rev {analysis.jobPostingRevision ?? "-"}</div>
              <div className="text-xs text-slate-500">
                {formatDateTime(analysis.createdAt)} · {analysis.confirmedAt ? "확정" : "미확정"}
              </div>
            </div>
            <div className="grid gap-2 sm:grid-cols-3">
              <Info label="고용 형태" value={formatTextValue(analysis.employmentType) || "-"} />
              <Info label="경력" value={formatTextValue(analysis.experienceLevel) || "-"} />
              <Info label="난이도" value={formatTextValue(analysis.difficulty) || "-"} />
            </div>
            <FieldBlock label="요약">
              <ExpandableText value={analysis.summary} emptyText="요약이 없습니다." />
            </FieldBlock>
            <SkillBadges label="필수 역량" values={parseStringArrayValue(analysis.requiredSkills)} />
            <SkillBadges label="우대 역량" values={parseStringArrayValue(analysis.preferredSkills)} />
            <AnalysisText label="담당 업무" value={analysis.duties} />
            <AnalysisText label="자격 요건" value={analysis.qualifications} />
            <AnalysisText label="근거" value={analysis.evidence} />
            <AnalysisText label="모호한 조건" value={analysis.ambiguousConditions} />
          </CardContent>
        </Card>
      ))}
    </GridSection>
  );
}

function CompanyAnalysisTab({ detail }: { detail: AdminApplicationCaseDetail }) {
  return (
    <GridSection title="기업 분석 이력" empty={detail.companyAnalyses.length === 0} emptyMessage="기업 분석 이력이 없습니다.">
      {detail.companyAnalyses.map((analysis) => (
        <Card key={analysis.id} className="border-slate-200 bg-white">
          <CardContent className="space-y-3 p-4 text-sm">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="font-semibold text-slate-900">#{analysis.id} · 공고 rev {analysis.jobPostingRevision ?? "-"}</div>
              <div className="text-xs text-slate-500">
                {formatDateTime(analysis.createdAt)} · {analysis.confirmedAt ? "확정" : "미확정"}
              </div>
            </div>
            <div className="grid gap-2 sm:grid-cols-3">
              <Info label="출처 유형" value={analysis.sourceType ?? "-"} />
              <Info label="확인 시각" value={formatDateTime(analysis.checkedAt)} />
              <Info label="갱신 권장" value={formatDateTime(analysis.refreshRecommendedAt)} />
            </div>
            <FieldBlock label="기업 요약">
              <ExpandableText value={analysis.companySummary} emptyText="요약이 없습니다." />
            </FieldBlock>
            <AnalysisText label="최근 이슈" value={analysis.recentIssues} />
            <AnalysisText label="산업" value={analysis.industry} />
            <AnalysisText label="경쟁사" value={analysis.competitors} />
            <AnalysisText label="면접 포인트" value={analysis.interviewPoints} />
            <AnalysisText label="출처" value={analysis.sources} />
            <AnalysisText label="검증된 사실" value={analysis.verifiedFacts} />
            <AnalysisText label="AI 추론" value={analysis.aiInferences} />
          </CardContent>
        </Card>
      ))}
    </GridSection>
  );
}

function AiLogsTab({ detail }: { detail: AdminApplicationCaseDetail }) {
  return (
    <GridSection title="B AI 사용량/실패 로그" empty={detail.usageLogs.length === 0} emptyMessage="AI 사용 로그가 없습니다.">
      {detail.usageLogs.map((log) => (
        <Card key={log.id} className={log.status === "FAILED" ? "border-red-200 bg-red-50" : "border-slate-200 bg-white"}>
          <CardContent className="space-y-3 p-4 text-sm">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className={log.status === "FAILED" ? "font-semibold text-red-800" : "font-semibold text-slate-900"}>
                {log.featureType} · {log.status}
              </div>
              <div className="text-xs text-slate-500">{formatDateTime(log.createdAt)}</div>
            </div>
            <div className="grid gap-2 sm:grid-cols-3">
              <Info label="모델" value={log.model ?? "-"} />
              <Info label="토큰" value={`${log.tokenUsage ?? 0}`} />
              <Info label="크레딧" value={`${log.creditUsed ?? 0}`} />
            </div>
            {log.errorMessage ? (
              <div className="rounded-md border border-red-200 bg-white p-3 text-xs font-medium text-red-700">
                <ExpandableText value={log.errorMessage} collapsedLines={4} emptyText="오류 메시지가 없습니다." />
              </div>
            ) : (
              <EmptyInline text="오류 메시지가 없습니다." />
            )}
          </CardContent>
        </Card>
      ))}
    </GridSection>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{label}</div>
      <div className="mt-1 truncate text-sm font-bold text-slate-900">{value}</div>
    </div>
  );
}

function FieldBlock({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{label}</div>
      <div className="mt-1">{children}</div>
    </div>
  );
}

function SkillBadges({ label, values }: { label: string; values: string[] }) {
  return (
    <FieldBlock label={label}>
      {values.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {values.map((value, index) => (
            <Badge key={`${value}-${index}`} variant="outline">
              {value}
            </Badge>
          ))}
        </div>
      ) : (
        <EmptyInline text={`${label} 정보가 없습니다.`} />
      )}
    </FieldBlock>
  );
}

function AnalysisText({ label, value }: { label: string; value: unknown }) {
  const normalized = formatTextValue(value);
  const parsed = parseJsonArrayOrText(normalized);
  const [expanded, setExpanded] = useState(false);

  if (parsed.kind === "empty") {
    return (
      <FieldBlock label={label}>
        <EmptyInline text={`${label} 정보가 없습니다.`} />
      </FieldBlock>
    );
  }

  if (parsed.kind === "list") {
    const visibleItems = expanded ? parsed.items : parsed.items.slice(0, 4);
    const canToggle = parsed.items.length > 4;

    return (
      <FieldBlock label={label}>
        <ul className="space-y-1.5 text-sm leading-6 text-slate-600">
          {visibleItems.map((item, index) => (
            <li key={`${item}-${index}`} className="flex gap-2">
              <span className="mt-2 size-1.5 shrink-0 rounded-full bg-current" />
              <span className="min-w-0 break-words">{item}</span>
            </li>
          ))}
        </ul>
        {canToggle && (
          <ExpandButton expanded={expanded} onClick={() => setExpanded((value) => !value)} />
        )}
      </FieldBlock>
    );
  }

  return (
    <FieldBlock label={label}>
      <ExpandableText value={parsed.text} emptyText={`${label} 정보가 없습니다.`} />
    </FieldBlock>
  );
}

function ExpandableText({
  value,
  emptyText,
  collapsedLines = 3,
}: {
  value: unknown;
  emptyText: string;
  collapsedLines?: 3 | 4 | 5 | 6;
}) {
  const text = formatTextValue(value);
  const [expanded, setExpanded] = useState(false);
  const hasLongText = text.length > 180 || text.split(/\r?\n/).length > collapsedLines + 1;
  const lineClampClass = {
    3: "line-clamp-3",
    4: "line-clamp-4",
    5: "line-clamp-5",
    6: "line-clamp-6",
  }[collapsedLines];

  if (!text) return <EmptyInline text={emptyText} />;

  return (
    <div className="space-y-2">
      <p className={`${expanded ? "" : lineClampClass} whitespace-pre-line break-words text-sm leading-6 text-slate-600`}>
        {text}
      </p>
      {hasLongText && <ExpandButton expanded={expanded} onClick={() => setExpanded((value) => !value)} />}
    </div>
  );
}

function ExpandButton({ expanded, onClick }: { expanded: boolean; onClick: () => void }) {
  return (
    <Button type="button" variant="ghost" size="sm" className="h-7 px-2 text-xs text-slate-600" onClick={onClick}>
      {expanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
      {expanded ? "접기" : "펼치기"}
    </Button>
  );
}

function EmptyInline({ text }: { text: string }) {
  return <p className="text-sm text-slate-400">{text}</p>;
}

function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center">
      <div className="text-sm font-semibold text-slate-700">{title}</div>
      {description && <div className="mt-1 text-xs leading-5 text-slate-500">{description}</div>}
    </div>
  );
}

function ErrorBox({ message }: { message: string }) {
  return <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{message}</div>;
}

function ListLoadingState() {
  return (
    <div className="space-y-2" aria-busy="true">
      {Array.from({ length: 5 }).map((_, index) => (
        <div key={index} className="rounded-lg border border-slate-200 bg-white p-3">
          <div className="h-4 w-3/4 animate-pulse rounded bg-slate-200" />
          <div className="mt-2 h-3 w-1/2 animate-pulse rounded bg-slate-100" />
          <div className="mt-3 h-3 w-2/3 animate-pulse rounded bg-slate-100" />
        </div>
      ))}
    </div>
  );
}

function DetailLoadingState() {
  return (
    <Card className="border-slate-200 bg-white" aria-busy="true">
      <CardContent className="space-y-4 p-5">
        <div className="h-6 w-2/5 animate-pulse rounded bg-slate-200" />
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="h-20 animate-pulse rounded-lg bg-slate-100" />
          ))}
        </div>
        <div className="h-44 animate-pulse rounded-lg bg-slate-100" />
      </CardContent>
    </Card>
  );
}

function GridSection({
  title,
  children,
  empty,
  emptyMessage,
}: {
  title: string;
  children: React.ReactNode;
  empty: boolean;
  emptyMessage?: string;
}) {
  return (
    <section className="space-y-2">
      <h2 className="text-sm font-bold text-slate-900">{title}</h2>
      {empty ? (
        <EmptyState title={emptyMessage ?? "표시할 데이터가 없습니다."} />
      ) : (
        <div className="grid gap-3 xl:grid-cols-2">{children}</div>
      )}
    </section>
  );
}
