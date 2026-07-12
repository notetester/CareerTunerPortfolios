import { useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import {
  AlertCircle,
  ArchiveRestore,
  Briefcase,
  CalendarDays,
  FileText,
  Loader2,
  Plus,
  RefreshCw,
  Search,
  Star,
  Trash2,
} from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Checkbox } from "@/app/components/ui/checkbox";
import { Input } from "@/app/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import { hideApplicationCaseFromTrash, restoreApplicationCase, updateApplicationCase } from "../api/applicationCasesApi";
import { ApplicationStatusBadge } from "../components/ApplicationStatusBadge";
import { LoginRequiredState } from "../components/LoginRequiredState";
import { useApplicationCases } from "../hooks/useApplicationCases";
import type { ApplicationCase, ApplicationStatus } from "../types/applicationCase";
import {
  APPLICATION_STATUS_OPTIONS,
  getApplicationSourceLabel,
} from "../types/applicationCase";
import { ApplicationExtractionBadge } from "../components/ApplicationExtractionBadge";
import { OcrRetryButton } from "../components/OcrRetryButton";
import { useApplicationCaseExtractions } from "../hooks/useApplicationCaseExtractions";
import type { ApplicationCaseExtraction } from "../types/applicationCase";
import { formatKoreaDate } from "../utils/dateFormat";

type ListMode = "active" | "trash";
type StatusFilter = "ALL" | ApplicationStatus;
type DeadlineFilter = "ALL" | "TODAY" | "WITHIN_7_DAYS" | "WITHIN_30_DAYS" | "NO_DEADLINE";
type SortOption = "CREATED_DESC" | "DEADLINE_ASC" | "DEADLINE_DESC" | "UPDATED_DESC";

const deadlineFilterOptions: { value: DeadlineFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "TODAY", label: "오늘까지" },
  { value: "WITHIN_7_DAYS", label: "7일 이내" },
  { value: "WITHIN_30_DAYS", label: "30일 이내" },
  { value: "NO_DEADLINE", label: "마감일 없음" },
];

const sortOptions: { value: SortOption; label: string }[] = [
  { value: "CREATED_DESC", label: "최근 등록순" },
  { value: "DEADLINE_ASC", label: "마감 빠른순" },
  { value: "DEADLINE_DESC", label: "마감 늦은순" },
  { value: "UPDATED_DESC", label: "최근 수정순" },
];

const DAY_MS = 24 * 60 * 60 * 1000;

function formatDate(value: string | null, emptyLabel = "미입력"): string {
  return formatKoreaDate(value, emptyLabel);
}

function toDateOnlyTime(value: string | null): number | null {
  if (!value) return null;
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) return null;
  return new Date(year, month - 1, day).getTime();
}

function toDateTime(value: string | null): number {
  if (!value) return 0;
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? 0 : time;
}

function compareRecent(a: ApplicationCase, b: ApplicationCase, key: "createdAt" | "updatedAt"): number {
  const diff = toDateTime(b[key]) - toDateTime(a[key]);
  return diff === 0 ? b.id - a.id : diff;
}

function compareDeadline(a: ApplicationCase, b: ApplicationCase, direction: "asc" | "desc"): number {
  const aTime = toDateOnlyTime(a.deadlineDate);
  const bTime = toDateOnlyTime(b.deadlineDate);
  if (aTime === null && bTime === null) return compareRecent(a, b, "createdAt");
  if (aTime === null) return 1;
  if (bTime === null) return -1;
  const diff = direction === "asc" ? aTime - bTime : bTime - aTime;
  return diff === 0 ? compareRecent(a, b, "createdAt") : diff;
}

function ApplicationCard({
  applicationCase,
  extraction,
  busy,
  retryingExtraction,
  mode,
  detailSection = "overview",
  onToggleFavorite,
  onRestore,
  onHideFromTrash,
  onRetryExtraction,
}: {
  applicationCase: ApplicationCase;
  extraction: ApplicationCaseExtraction | null | undefined;
  busy: boolean;
  retryingExtraction: boolean;
  mode: ListMode;
  /** 헤더 메뉴 의도(?tab=fit|strategy|learning) — 카드 클릭 시 이동할 상세 섹션. */
  detailSection?: string;
  onToggleFavorite(applicationCase: ApplicationCase): void;
  onRestore(applicationCase: ApplicationCase): void;
  onHideFromTrash(applicationCase: ApplicationCase): void;
  onRetryExtraction(applicationCase: ApplicationCase, ocrProvider: string): void;
}) {
  const isTrash = mode === "trash";
  const title = (
    <div className="min-w-0">
      <div className="truncate text-sm font-bold text-slate-900">{applicationCase.companyName}</div>
      <div className="truncate text-xs text-slate-500">{applicationCase.jobTitle}</div>
    </div>
  );

  return (
    <Card className="h-full border-slate-200 bg-card transition-shadow hover:shadow-md">
      <CardContent className="flex h-full flex-col gap-4 p-5">
        <div className="flex items-start justify-between gap-3">
          {isTrash ? (
            <div className="min-w-0 flex-1">{title}</div>
          ) : (
            <Link to={`/applications/${applicationCase.id}/${detailSection}`} className="min-w-0 flex-1">
              {title}
            </Link>
          )}
          {isTrash ? (
            <div className="flex shrink-0 gap-2">
              <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={busy}
                onClick={() => onRestore(applicationCase)}
              >
                <ArchiveRestore className="size-4" />
                복원
              </Button>
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="border-red-200 text-red-700 hover:bg-red-50 hover:text-red-800"
                disabled={busy}
                onClick={() => onHideFromTrash(applicationCase)}
              >
                <Trash2 className="size-4" />
                완전 삭제
              </Button>
            </div>
          ) : (
            <button
              type="button"
              className="rounded-md p-1.5 text-slate-400 hover:bg-amber-50 hover:text-amber-500 disabled:opacity-50"
              disabled={busy}
              aria-label="즐겨찾기 변경"
              onClick={() => onToggleFavorite(applicationCase)}
            >
              <Star className={`size-4 ${applicationCase.favorite ? "fill-amber-400 text-amber-400" : ""}`} />
            </button>
          )}
        </div>

        <div className="flex flex-wrap gap-2">
          <ApplicationStatusBadge status={applicationCase.status} />
          <ApplicationExtractionBadge extraction={extraction} />
          <Badge variant="outline" className="border-slate-200 bg-slate-50 text-slate-600">
            {getApplicationSourceLabel(applicationCase.sourceType)}
          </Badge>
          {applicationCase.archived && (
            <Badge variant="outline" className="border-slate-200 bg-slate-100 text-slate-500">
              보관됨
            </Badge>
          )}
          {isTrash && (
            <Badge variant="outline" className="border-red-200 bg-red-50 text-red-700">
              삭제함
            </Badge>
          )}
        </div>

        {!isTrash && extraction?.status === "FAILED" && (
          <OcrRetryButton
            sourceType={extraction.sourceType}
            retrying={retryingExtraction}
            disabled={busy}
            onRetry={(provider) => onRetryExtraction(applicationCase, provider)}
            className="w-fit border-red-200 text-red-700 hover:bg-red-50 hover:text-red-800"
          />
        )}

        <div className="mt-auto flex items-end justify-between gap-3 border-t border-slate-100 pt-3 text-xs text-slate-500">
          <div className="min-w-0 space-y-1">
            <span className="flex items-center gap-1.5">
              <CalendarDays className="size-3.5" />
              등록 {formatDate(applicationCase.createdAt)}
            </span>
            <span className="flex items-center gap-1.5">
              <CalendarDays className="size-3.5" />
              마감 {formatDate(applicationCase.deadlineDate, "마감일 없음/상시채용")}
            </span>
            {isTrash && (
              <span className="flex items-center gap-1.5 text-red-600">
                <Trash2 className="size-3.5" />
                삭제함 이동 {formatDate(applicationCase.deletedAt)}
              </span>
            )}
          </div>
          {!isTrash && (
            <Link className="font-semibold text-blue-600 hover:text-blue-700" to={`/applications/${applicationCase.id}/${detailSection}`}>
              상세 보기
            </Link>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export function ApplicationListPage({ mode = "active" }: { mode?: ListMode }) {
  const navigate = useNavigate();
  // 헤더 하위메뉴(내 스펙과 비교·지원 전략·학습/자격증 추천)의 ?tab= 의도를 카드 클릭 목적지로 연결한다.
  // 세 항목 모두 상세의 '적합도' 탭(비교/전략/학습 패널이 그 안에 있음)으로 딥링크.
  const [searchParams] = useSearchParams();
  const tabIntent = searchParams.get("tab");
  const detailSection = tabIntent === "fit" || tabIntent === "strategy" || tabIntent === "learning" ? "fit" : "overview";
  const { user, loading: authLoading, isAuthenticated } = useAuth();
  const isTrash = mode === "trash";
  const [includeArchived, setIncludeArchived] = useState(false);
  const { applicationCases, setApplicationCases, loading, error, refresh } = useApplicationCases(
    isAuthenticated,
    isTrash ? { view: "DELETED" } : includeArchived,
    user?.id ?? null,
  );
  const applicationCaseIds = useMemo(
    () => applicationCases.map((item) => item.id),
    [applicationCases],
  );
  const {
    extractions,
    retryingId: retryingExtractionId,
    error: extractionError,
    retry: retryExtraction,
  } = useApplicationCaseExtractions(applicationCaseIds, isAuthenticated && !isTrash);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [deadlineFilter, setDeadlineFilter] = useState<DeadlineFilter>("ALL");
  const [favoriteOnly, setFavoriteOnly] = useState(false);
  const [sortOption, setSortOption] = useState<SortOption>("CREATED_DESC");
  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    const today = new Date();
    const todayTime = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
    const sevenDaysLater = todayTime + 7 * DAY_MS;
    const thirtyDaysLater = todayTime + 30 * DAY_MS;

    const nextItems = applicationCases.filter((item) => {
      const matchKeyword =
        !keyword ||
        item.companyName.toLowerCase().includes(keyword) ||
        item.jobTitle.toLowerCase().includes(keyword);
      const matchStatus = statusFilter === "ALL" || item.status === statusFilter;
      const deadlineTime = toDateOnlyTime(item.deadlineDate);
      const matchDeadline =
        deadlineFilter === "ALL" ||
        (deadlineFilter === "TODAY" && deadlineTime !== null && deadlineTime <= todayTime) ||
        (deadlineFilter === "WITHIN_7_DAYS" && deadlineTime !== null && deadlineTime >= todayTime && deadlineTime <= sevenDaysLater) ||
        (deadlineFilter === "WITHIN_30_DAYS" && deadlineTime !== null && deadlineTime >= todayTime && deadlineTime <= thirtyDaysLater) ||
        (deadlineFilter === "NO_DEADLINE" && deadlineTime === null);
      const matchFavorite = !favoriteOnly || item.favorite;
      return matchKeyword && matchStatus && matchDeadline && matchFavorite;
    });

    return nextItems.sort((a, b) => {
      if (sortOption === "DEADLINE_ASC") return compareDeadline(a, b, "asc");
      if (sortOption === "DEADLINE_DESC") return compareDeadline(a, b, "desc");
      if (sortOption === "UPDATED_DESC") return compareRecent(a, b, "updatedAt");
      return compareRecent(a, b, "createdAt");
    });
  }, [applicationCases, deadlineFilter, favoriteOnly, search, sortOption, statusFilter]);

  const hasActiveFilter = search.trim() !== "" || statusFilter !== "ALL" || deadlineFilter !== "ALL" || favoriteOnly;

  const summary = useMemo(
    () => ({
      total: applicationCases.length,
      ready: applicationCases.filter((item) => item.status === "READY").length,
      analyzing: applicationCases.filter((item) => item.status === "ANALYZING").length,
      favorite: applicationCases.filter((item) => item.favorite).length,
      archived: applicationCases.filter((item) => item.archived).length,
    }),
    [applicationCases],
  );

  const handleToggleFavorite = async (applicationCase: ApplicationCase) => {
    setBusyId(applicationCase.id);
    setActionError(null);
    try {
      const updated = await updateApplicationCase(applicationCase.id, {
        favorite: !applicationCase.favorite,
      });
      setApplicationCases((items) => items.map((item) => (item.id === updated.id ? updated : item)));
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "즐겨찾기 변경에 실패했습니다.");
    } finally {
      setBusyId(null);
    }
  };

  const handleRestore = async (applicationCase: ApplicationCase) => {
    setBusyId(applicationCase.id);
    setActionError(null);
    try {
      await restoreApplicationCase(applicationCase.id);
      setApplicationCases((items) => items.filter((item) => item.id !== applicationCase.id));
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "지원 건을 복원하지 못했습니다.");
    } finally {
      setBusyId(null);
    }
  };

  const handleHideFromTrash = async (applicationCase: ApplicationCase) => {
    const ok = window.confirm(
      `'${applicationCase.companyName}' 지원 건을 삭제함에서 완전히 지웁니다.\n삭제함 목록에서도 더 이상 보이지 않습니다. 계속할까요?`,
    );
    if (!ok) return;
    setBusyId(applicationCase.id);
    setActionError(null);
    try {
      await hideApplicationCaseFromTrash(applicationCase.id);
      setApplicationCases((items) => items.filter((item) => item.id !== applicationCase.id));
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "지원 건을 완전히 삭제하지 못했습니다.");
    } finally {
      setBusyId(null);
    }
  };

  if (authLoading) {
    return (
      <div className="min-h-[calc(100vh-72px)] bg-slate-50 px-4 py-10">
        <div className="mx-auto max-w-6xl">
          <div className="h-32 animate-pulse rounded-lg bg-slate-200" />
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <LoginRequiredState
        title="지원 건 관리는 로그인 후 사용할 수 있습니다"
        description="본인 지원 건 목록과 공고문은 인증된 사용자 데이터로만 조회합니다."
      />
    );
  }

  return (
    <div className="min-h-[calc(100vh-72px)] bg-slate-50">
      <div className="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6">
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-bold text-slate-950">
              {isTrash ? <Trash2 className="size-6 text-red-600" /> : <Briefcase className="size-6 text-blue-600" />}
              {isTrash ? "삭제함" : "지원 건 관리"}
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              {isTrash ? "삭제함으로 이동한 지원 건을 확인하고 활성 목록으로 복원합니다. 이동 후 30일이 지나면 삭제함 목록에서 숨겨집니다." : "기업과 직무별 지원 준비 단위를 관리합니다."}
            </p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button variant="outline" onClick={() => navigate(isTrash ? "/applications" : "/applications/trash")}>
              {isTrash ? <Briefcase className="size-4" /> : <Trash2 className="size-4" />}
              {isTrash ? "활성 목록" : "삭제함"}
            </Button>
            {!isTrash && (
              <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => navigate("/applications/new")}>
                <Plus className="size-4" />
                새 지원 건
              </Button>
            )}
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          {[
            { label: isTrash ? "삭제함" : "전체", value: summary.total, icon: isTrash ? Trash2 : Briefcase },
            { label: "준비중", value: summary.ready, icon: FileText },
            { label: "분석중", value: summary.analyzing, icon: RefreshCw },
            { label: "즐겨찾기", value: summary.favorite, icon: Star },
            { label: "보관", value: summary.archived, icon: FileText },
          ].map((item) => (
            <div key={item.label} className="rounded-lg border border-slate-200 bg-card p-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-slate-500">{item.label}</span>
                <item.icon className="size-4 text-slate-400" />
              </div>
              <div className="mt-2 text-2xl font-bold text-slate-950">{item.value}</div>
            </div>
          ))}
        </div>

        <Card className="border-slate-200 bg-card">
          <CardContent className="space-y-3 p-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
              <div className="relative max-w-md flex-1">
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <Input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="기업명, 직무명 검색"
                  className="pl-9"
                />
              </div>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <Select value={sortOption} onValueChange={(value) => setSortOption(value as SortOption)}>
                  <SelectTrigger className="w-full bg-card sm:w-40">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {sortOptions.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {!isTrash && (
                  <>
                    <label className="flex items-center gap-2 rounded-md bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-600">
                      <Checkbox
                        checked={includeArchived}
                        onCheckedChange={(checked) => setIncludeArchived(Boolean(checked))}
                      />
                      보관 포함
                    </label>
                    <label className="flex items-center gap-2 rounded-md bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-600">
                      <Checkbox
                        checked={favoriteOnly}
                        onCheckedChange={(checked) => setFavoriteOnly(Boolean(checked))}
                      />
                      즐겨찾기만
                    </label>
                  </>
                )}
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
              <div className="flex flex-wrap items-center gap-2">
                <span className="flex items-center gap-2 rounded-md bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-600">
                  상태
                </span>
                <button
                  type="button"
                  className={`rounded-md px-3 py-2 text-xs font-semibold ${
                    statusFilter === "ALL" ? "bg-foreground text-background" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                  }`}
                  onClick={() => setStatusFilter("ALL")}
                >
                  전체
                </button>
                {APPLICATION_STATUS_OPTIONS.map((option) => (
                  <button
                    type="button"
                    key={option.value}
                    className={`rounded-md px-3 py-2 text-xs font-semibold ${
                      statusFilter === option.value
                        ? "bg-foreground text-background"
                        : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                    }`}
                    onClick={() => setStatusFilter(option.value)}
                  >
                    {option.label}
                  </button>
                ))}
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <span className="flex items-center gap-2 rounded-md bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-600">
                  마감
                </span>
                {deadlineFilterOptions.map((option) => (
                  <button
                    type="button"
                    key={option.value}
                    className={`rounded-md px-3 py-2 text-xs font-semibold ${
                      deadlineFilter === option.value
                        ? "bg-blue-600 text-white"
                        : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                    }`}
                    onClick={() => setDeadlineFilter(option.value)}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
          </CardContent>
        </Card>

        {(error || actionError || extractionError) && (
          <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            <AlertCircle className="mt-0.5 size-4 shrink-0" />
            <div className="flex-1">{actionError ?? error ?? extractionError}</div>
            {error && (
              <button className="font-semibold" type="button" onClick={() => void refresh()}>
                다시 시도
              </button>
            )}
          </div>
        )}

        {loading ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className="h-44 animate-pulse rounded-lg bg-slate-200" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <Card className="border-dashed border-slate-300 bg-card">
            <CardContent className="flex flex-col items-center gap-4 p-10 text-center">
              <div className="flex size-12 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                {isTrash ? <Trash2 className="size-6" /> : <Briefcase className="size-6" />}
              </div>
              <div>
                <div className="font-semibold text-slate-900">
                  {hasActiveFilter ? "조건에 맞는 지원 건이 없습니다" : isTrash ? "삭제함에 지원 건이 없습니다" : "지원 건이 없습니다"}
                </div>
                <p className="mt-1 text-sm text-slate-500">
                  {hasActiveFilter ? "검색어와 필터 조건을 다시 확인하세요." : isTrash ? "삭제함으로 이동한 지 30일 이내인 지원 건이 이곳에 표시됩니다." : "새 지원 건을 만들고 공고문을 등록하세요."}
                </p>
              </div>
              {!isTrash && (
                <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => navigate("/applications/new")}>
                  <Plus className="size-4" />
                  새 지원 건
                </Button>
              )}
            </CardContent>
          </Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {!isTrash && (
              <button
                type="button"
                className="flex min-h-44 flex-col items-center justify-center gap-3 rounded-lg border-2 border-dashed border-slate-300 bg-card p-6 text-slate-500 transition-colors hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700"
                onClick={() => navigate("/applications/new")}
              >
                <Plus className="size-7" />
                <span className="text-sm font-semibold">새 지원 건</span>
              </button>
            )}
            {filtered.map((applicationCase) => (
              <ApplicationCard
                key={applicationCase.id}
                applicationCase={applicationCase}
                extraction={extractions[applicationCase.id]}
                busy={busyId === applicationCase.id || retryingExtractionId === applicationCase.id}
                retryingExtraction={retryingExtractionId === applicationCase.id}
                mode={mode}
                detailSection={detailSection}
                onToggleFavorite={(item) => void handleToggleFavorite(item)}
                onRestore={(item) => void handleRestore(item)}
                onHideFromTrash={(item) => void handleHideFromTrash(item)}
                onRetryExtraction={(item, provider) => void retryExtraction(item.id, provider)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
