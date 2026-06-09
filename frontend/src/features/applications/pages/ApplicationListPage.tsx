import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import {
  AlertCircle,
  Briefcase,
  CalendarDays,
  FileText,
  Plus,
  RefreshCw,
  Search,
  Star,
} from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Checkbox } from "@/app/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import { updateApplicationCase } from "../api/applicationCasesApi";
import { ApplicationStatusBadge } from "../components/ApplicationStatusBadge";
import { LoginRequiredState } from "../components/LoginRequiredState";
import { useApplicationCases } from "../hooks/useApplicationCases";
import type { ApplicationCase, ApplicationStatus } from "../types/applicationCase";
import {
  APPLICATION_STATUS_OPTIONS,
  getApplicationSourceLabel,
} from "../types/applicationCase";

type StatusFilter = "ALL" | ApplicationStatus;
type DeadlineFilter = "ALL" | "TODAY" | "WITHIN_7_DAYS" | "WITHIN_30_DAYS" | "NO_DEADLINE";
type SortOption = "CREATED_DESC" | "DEADLINE_ASC" | "DEADLINE_DESC" | "UPDATED_DESC";

const deadlineFilterOptions: { value: DeadlineFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "TODAY", label: "오늘까지" },
  { value: "WITHIN_7_DAYS", label: "7일 이내" },
  { value: "WITHIN_30_DAYS", label: "30일 이내" },
  { value: "NO_DEADLINE", label: "마감일 미입력" },
];

const sortOptions: { value: SortOption; label: string }[] = [
  { value: "CREATED_DESC", label: "최근 등록 순" },
  { value: "DEADLINE_ASC", label: "마감 임박 순" },
  { value: "DEADLINE_DESC", label: "마감 늦은 순" },
  { value: "UPDATED_DESC", label: "최근 수정 순" },
];

const DAY_MS = 24 * 60 * 60 * 1000;

function formatDate(value: string | null, emptyLabel = "미입력"): string {
  if (!value) return emptyLabel;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium" }).format(new Date(value));
}

function toDateOnlyTime(value: string | null): number | null {
  if (!value) return null;
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) return null;
  return new Date(year, month - 1, day).getTime();
}

function toDateTime(value: string): number {
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
  busy,
  onToggleFavorite,
}: {
  applicationCase: ApplicationCase;
  busy: boolean;
  onToggleFavorite(applicationCase: ApplicationCase): void;
}) {
  return (
    <Card className="h-full border-slate-200 bg-white transition-shadow hover:shadow-md">
      <CardContent className="flex h-full flex-col gap-4 p-5">
        <div className="flex items-start justify-between gap-3">
          <Link to={`/applications/${applicationCase.id}`} className="min-w-0 flex-1">
            <div className="flex items-center gap-3">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-slate-900 text-sm font-bold text-white">
                {applicationCase.companyName.slice(0, 1)}
              </div>
              <div className="min-w-0">
                <div className="truncate text-sm font-bold text-slate-900">{applicationCase.companyName}</div>
                <div className="truncate text-xs text-slate-500">{applicationCase.jobTitle}</div>
              </div>
            </div>
          </Link>
          <button
            type="button"
            className="rounded-md p-1.5 text-slate-400 hover:bg-amber-50 hover:text-amber-500 disabled:opacity-50"
            disabled={busy}
            aria-label="즐겨찾기 변경"
            onClick={() => onToggleFavorite(applicationCase)}
          >
            <Star className={`size-4 ${applicationCase.favorite ? "fill-amber-400 text-amber-400" : ""}`} />
          </button>
        </div>

        <div className="flex flex-wrap gap-2">
          <ApplicationStatusBadge status={applicationCase.status} />
          <Badge variant="outline" className="border-slate-200 bg-slate-50 text-slate-600">
            {getApplicationSourceLabel(applicationCase.sourceType)}
          </Badge>
          {applicationCase.archived && (
            <Badge variant="outline" className="border-slate-200 bg-slate-100 text-slate-500">
              보관됨
            </Badge>
          )}
        </div>

        <div className="mt-auto flex items-end justify-between gap-3 border-t border-slate-100 pt-3 text-xs text-slate-500">
          <div className="min-w-0 space-y-1">
            <span className="flex items-center gap-1.5">
              <CalendarDays className="size-3.5" />
              공고 {formatDate(applicationCase.postingDate)}
            </span>
            <span className="flex items-center gap-1.5">
              <CalendarDays className="size-3.5" />
              마감 {formatDate(applicationCase.deadlineDate)}
            </span>
          </div>
          <Link className="font-semibold text-blue-600 hover:text-blue-700" to={`/applications/${applicationCase.id}`}>
            상세 보기
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}

export function ApplicationListPage() {
  const navigate = useNavigate();
  const { loading: authLoading, isAuthenticated } = useAuth();
  const [includeArchived, setIncludeArchived] = useState(false);
  const { applicationCases, setApplicationCases, loading, error, refresh } = useApplicationCases(isAuthenticated, includeArchived);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [deadlineFilter, setDeadlineFilter] = useState<DeadlineFilter>("ALL");
  const [sortOption, setSortOption] = useState<SortOption>("CREATED_DESC");
  const [favoriteBusyId, setFavoriteBusyId] = useState<number | null>(null);
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
      return matchKeyword && matchStatus && matchDeadline;
    });

    return nextItems.sort((a, b) => {
      if (sortOption === "DEADLINE_ASC") return compareDeadline(a, b, "asc");
      if (sortOption === "DEADLINE_DESC") return compareDeadline(a, b, "desc");
      if (sortOption === "UPDATED_DESC") return compareRecent(a, b, "updatedAt");
      return compareRecent(a, b, "createdAt");
    });
  }, [applicationCases, deadlineFilter, search, sortOption, statusFilter]);

  const hasActiveFilter = search.trim() !== "" || statusFilter !== "ALL" || deadlineFilter !== "ALL";

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
    setFavoriteBusyId(applicationCase.id);
    setActionError(null);
    try {
      const updated = await updateApplicationCase(applicationCase.id, {
        favorite: !applicationCase.favorite,
      });
      setApplicationCases((items) => items.map((item) => (item.id === updated.id ? updated : item)));
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "즐겨찾기 변경에 실패했습니다.");
    } finally {
      setFavoriteBusyId(null);
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
              <Briefcase className="size-6 text-blue-600" />
              지원 건 관리
            </h1>
            <p className="mt-1 text-sm text-slate-500">기업과 직무별 지원 준비 단위를 관리합니다.</p>
          </div>
          <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => navigate("/applications/new")}>
            <Plus className="size-4" />
            새 지원 건
          </Button>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          {[
            { label: "전체", value: summary.total, icon: Briefcase },
            { label: "준비중", value: summary.ready, icon: FileText },
            { label: "분석중", value: summary.analyzing, icon: RefreshCw },
            { label: "즐겨찾기", value: summary.favorite, icon: Star },
            { label: "보관", value: summary.archived, icon: FileText },
          ].map((item) => (
            <div key={item.label} className="rounded-lg border border-slate-200 bg-white p-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-slate-500">{item.label}</span>
                <item.icon className="size-4 text-slate-400" />
              </div>
              <div className="mt-2 text-2xl font-bold text-slate-950">{item.value}</div>
            </div>
          ))}
        </div>

        <Card className="border-slate-200 bg-white">
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
                  <SelectTrigger className="w-full bg-white sm:w-40">
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
                <label className="flex items-center gap-2 rounded-md bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-600">
                  <Checkbox
                    checked={includeArchived}
                    onCheckedChange={(checked) => setIncludeArchived(Boolean(checked))}
                  />
                  보관 포함
                </label>
              </div>
            </div>

            <div className="flex flex-wrap gap-2">
              <span className="flex items-center gap-2 rounded-md bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-600">
                상태
              </span>
              <button
                type="button"
                className={`rounded-md px-3 py-2 text-xs font-semibold ${
                  statusFilter === "ALL" ? "bg-slate-900 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
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
                      ? "bg-slate-900 text-white"
                      : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                  }`}
                  onClick={() => setStatusFilter(option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>

            <div className="flex flex-wrap gap-2">
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
          </CardContent>
        </Card>

        {(error || actionError) && (
          <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            <AlertCircle className="mt-0.5 size-4 shrink-0" />
            <div className="flex-1">{actionError ?? error}</div>
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
          <Card className="border-dashed border-slate-300 bg-white">
            <CardContent className="flex flex-col items-center gap-4 p-10 text-center">
              <div className="flex size-12 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                <Briefcase className="size-6" />
              </div>
              <div>
                <div className="font-semibold text-slate-900">
                  {hasActiveFilter ? "조건에 맞는 지원 건이 없습니다" : "지원 건이 없습니다"}
                </div>
                <p className="mt-1 text-sm text-slate-500">
                  {hasActiveFilter ? "검색어와 필터 조건을 다시 확인하세요." : "새 지원 건을 만들고 공고문을 등록하세요."}
                </p>
              </div>
              <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => navigate("/applications/new")}>
                <Plus className="size-4" />
                새 지원 건
              </Button>
            </CardContent>
          </Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            <button
              type="button"
              className="flex min-h-44 flex-col items-center justify-center gap-3 rounded-lg border-2 border-dashed border-slate-300 bg-white p-6 text-slate-500 transition-colors hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700"
              onClick={() => navigate("/applications/new")}
            >
              <Plus className="size-7" />
              <span className="text-sm font-semibold">새 지원 건</span>
            </button>
            {filtered.map((applicationCase) => (
              <ApplicationCard
                key={applicationCase.id}
                applicationCase={applicationCase}
                busy={favoriteBusyId === applicationCase.id}
                onToggleFavorite={(item) => void handleToggleFavorite(item)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
