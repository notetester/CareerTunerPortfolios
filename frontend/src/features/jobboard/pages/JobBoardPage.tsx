import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router";
import { Briefcase, Building2, Clock3, Eye, MapPin, Search } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import {
  CAREER_LEVEL_LABELS,
  EMPLOYMENT_TYPE_LABELS,
  TRUST_GRADE_LABELS,
  type CompanyJobPosting,
} from "@/features/company/types/company";
import { searchJobPostings } from "../api/jobboardApi";
import type { JobBoardSearchParams } from "../types/jobboard";

const PAGE_SIZE = 10;

const SORT_OPTIONS: Array<{ value: NonNullable<JobBoardSearchParams["sort"]>; label: string }> = [
  { value: "latest", label: "최신순" },
  { value: "deadline", label: "마감임박순" },
  { value: "views", label: "조회순" },
];

const selectClass = "h-10 rounded-md border border-slate-200 bg-white px-3 text-sm";

function formatDate(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString("ko-KR");
}

/** 마감 표기 — 상시면 "상시채용", 날짜가 있으면 D-day. */
function deadlineLabel(posting: CompanyJobPosting): string {
  if (posting.alwaysOpen) return "상시채용";
  if (!posting.deadlineDate) return "마감일 미정";
  const deadline = new Date(posting.deadlineDate);
  if (Number.isNaN(deadline.getTime())) return posting.deadlineDate;
  const days = Math.ceil((deadline.getTime() - Date.now()) / 86_400_000);
  if (days < 0) return "마감";
  if (days === 0) return "오늘 마감";
  return `D-${days}`;
}

/** 공개 채용공고 게시판 — 키워드/직무/지역/고용형태/경력 필터 + 정렬 + 페이징. */
export function JobBoardPage() {
  const [keyword, setKeyword] = useState("");
  const [jobRole, setJobRole] = useState("");
  const [location, setLocation] = useState("");
  const [employmentType, setEmploymentType] = useState("");
  const [careerLevel, setCareerLevel] = useState("");
  const [sort, setSort] = useState<NonNullable<JobBoardSearchParams["sort"]>>("latest");
  const [page, setPage] = useState(0);

  const [items, setItems] = useState<CompanyJobPosting[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (nextPage: number) => {
      setLoading(true);
      setError(null);
      try {
        const result = await searchJobPostings({
          keyword: keyword.trim() || undefined,
          jobRole: jobRole.trim() || undefined,
          location: location.trim() || undefined,
          employmentType: employmentType || undefined,
          careerLevel: careerLevel || undefined,
          sort,
          page: nextPage,
          size: PAGE_SIZE,
        });
        setItems(result.items);
        setTotal(result.total);
        setPage(result.page);
      } catch (requestError) {
        setError(requestError instanceof Error ? requestError.message : "공고 목록을 불러오지 못했습니다.");
      } finally {
        setLoading(false);
      }
    },
    [keyword, jobRole, location, employmentType, careerLevel, sort],
  );

  useEffect(() => {
    void load(0);
    // 정렬 변경 시 즉시 재조회(텍스트 필터는 검색 버튼으로 트리거)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sort, employmentType, careerLevel]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="mx-auto max-w-5xl space-y-4 px-4 py-8">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold text-slate-900">
          <Briefcase className="size-5" />
          채용공고
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          기업이 직접 등록한 공고입니다. 마음에 드는 공고는 바로 지원 건으로 만들어 분석할 수 있습니다.
        </p>
      </div>

      {/* 필터 바 */}
      <Card className="border-slate-200">
        <CardContent className="space-y-3 pt-6">
          <div className="grid gap-2 md:grid-cols-3">
            <Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="키워드 (제목/직무/기업명)" onKeyDown={(e) => e.key === "Enter" && void load(0)} />
            <Input value={jobRole} onChange={(e) => setJobRole(e.target.value)} placeholder="직무 (예: 백엔드)" onKeyDown={(e) => e.key === "Enter" && void load(0)} />
            <Input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="지역 (예: 서울)" onKeyDown={(e) => e.key === "Enter" && void load(0)} />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select className={selectClass} value={employmentType} onChange={(e) => setEmploymentType(e.target.value)}>
              <option value="">고용형태 전체</option>
              {Object.entries(EMPLOYMENT_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
            <select className={selectClass} value={careerLevel} onChange={(e) => setCareerLevel(e.target.value)}>
              <option value="">경력 전체</option>
              {Object.entries(CAREER_LEVEL_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
            <select className={selectClass} value={sort} onChange={(e) => setSort(e.target.value as typeof sort)}>
              {SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
            <Button className="ml-auto bg-blue-600 text-white hover:bg-blue-700" onClick={() => void load(0)}>
              <Search className="size-4" />
              검색
            </Button>
          </div>
        </CardContent>
      </Card>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      {/* 목록 */}
      {loading ? (
        <div className="py-16 text-center text-sm text-slate-500">공고를 불러오는 중...</div>
      ) : items.length === 0 ? (
        <div className="rounded-lg border border-slate-200 bg-white py-16 text-center text-sm text-slate-500">
          조건에 맞는 공고가 없습니다.
        </div>
      ) : (
        <div className="space-y-3">
          <p className="text-sm text-slate-500">총 {total.toLocaleString()}건</p>
          {items.map((posting) => (
            <Link key={posting.id} to={`/jobs/${posting.id}`} className="block">
              <Card className="border-slate-200 transition-colors hover:border-blue-300 hover:bg-blue-50/30">
                <CardContent className="pt-6">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <div className="flex flex-wrap items-center gap-2 text-sm text-slate-500">
                        <Building2 className="size-4" />
                        <span>{posting.companyName ?? "기업"}</span>
                        {posting.trustGrade && posting.trustGrade !== "BASIC" && (
                          <Badge className="bg-blue-100 text-blue-700">{TRUST_GRADE_LABELS[posting.trustGrade]}</Badge>
                        )}
                      </div>
                      <h2 className="mt-1 text-base font-semibold text-slate-900">{posting.title}</h2>
                      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-slate-600">
                        <span>{posting.jobRole}</span>
                        <span>{EMPLOYMENT_TYPE_LABELS[posting.employmentType] ?? posting.employmentType}</span>
                        <span>{CAREER_LEVEL_LABELS[posting.careerLevel] ?? posting.careerLevel}</span>
                        {posting.workLocation && (
                          <span className="flex items-center gap-1"><MapPin className="size-3.5" />{posting.workLocation}</span>
                        )}
                      </div>
                      {posting.tags.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {posting.tags.slice(0, 6).map((tag) => (
                            <span key={tag} className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">#{tag}</span>
                          ))}
                        </div>
                      )}
                    </div>
                    <div className="flex flex-col items-end gap-1 text-xs text-slate-500">
                      <Badge className="bg-amber-100 text-amber-700">
                        <Clock3 className="mr-1 size-3" />
                        {deadlineLabel(posting)}
                      </Badge>
                      <span className="flex items-center gap-1"><Eye className="size-3.5" />{posting.viewCount.toLocaleString()}</span>
                      <span>게시 {formatDate(posting.publishedAt)}</span>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))}

          {/* 페이징 */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-2">
              <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => void load(page - 1)}>
                이전
              </Button>
              <span className="text-sm text-slate-600">{page + 1} / {totalPages}</span>
              <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => void load(page + 1)}>
                다음
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
