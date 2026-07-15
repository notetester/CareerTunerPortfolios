import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import {
  ArrowLeft, Banknote, Building2, CalendarDays, Clock3, Eye,
  GraduationCap, MapPin, Sparkles, Users,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import {
  CAREER_LEVEL_LABELS,
  EDUCATION_LEVEL_LABELS,
  EMPLOYMENT_TYPE_LABELS,
  TRUST_GRADE_LABELS,
  type CompanyJobPosting,
} from "@/features/company/types/company";
import { analyzeJobPosting, getJobPosting } from "../api/jobboardApi";

function formatDate(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString("ko-KR");
}

/** 경력 조건 표기 — 경력직이면 연차 범위를 함께 보여준다. */
function careerText(posting: CompanyJobPosting): string {
  const base = CAREER_LEVEL_LABELS[posting.careerLevel] ?? posting.careerLevel;
  if (posting.careerLevel !== "EXPERIENCED") return base;
  const min = posting.careerYearsMin;
  const max = posting.careerYearsMax;
  if (min != null && max != null) return `${base} ${min}~${max}년`;
  if (min != null) return `${base} ${min}년 이상`;
  if (max != null) return `${base} ${max}년 이하`;
  return base;
}

/** 본문 섹션(주요업무/자격요건 등) — 값이 없으면 렌더하지 않는다. */
function Section({ title, body }: { title: string; body: string | null }) {
  if (!body) return null;
  return (
    <div>
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">{body}</p>
    </div>
  );
}

/** 공개 채용공고 상세 — "이 공고로 분석하기"로 기존 지원 건 파이프라인에 연결한다. */
export function JobDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [posting, setPosting] = useState<CompanyJobPosting | null>(null);
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const postingId = Number(id);
    if (!Number.isFinite(postingId)) {
      setError("잘못된 공고 주소입니다.");
      setLoading(false);
      return;
    }
    setLoading(true);
    getJobPosting(postingId)
      .then(setPosting)
      .catch((requestError) =>
        setError(requestError instanceof Error ? requestError.message : "공고를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [id]);

  const analyze = async () => {
    if (!posting) return;
    setAnalyzing(true);
    setError(null);
    try {
      const result = await analyzeJobPosting(posting.id);
      // 생성된 지원 건 상세로 이동 — 이후 공고 분석/적합도 분석은 기존 플로우 그대로.
      navigate(`/applications/${result.applicationCaseId}`);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "지원 건 생성에 실패했습니다.");
      setAnalyzing(false);
    }
  };

  if (loading) {
    return <div className="mx-auto w-full max-w-[1400px] px-4 py-16 text-center text-sm text-slate-500">공고를 불러오는 중...</div>;
  }

  if (!posting) {
    return (
      <div className="mx-auto w-full max-w-[1400px] space-y-4 px-4 py-16 text-center">
        <p className="text-sm text-slate-500">{error ?? "게시 중인 공고를 찾을 수 없습니다."}</p>
        <Link to="/jobs" className="text-sm text-blue-600 hover:underline">목록으로 돌아가기</Link>
      </div>
    );
  }

  const summaryRows: Array<{ icon: typeof MapPin; label: string; value: string }> = [
    { icon: Users, label: "경력", value: careerText(posting) },
    { icon: GraduationCap, label: "학력", value: EDUCATION_LEVEL_LABELS[posting.educationLevel] ?? posting.educationLevel },
    {
      icon: Banknote,
      label: "급여",
      value: `${posting.salaryText ?? "회사 내규에 따름"}${posting.salaryNegotiable ? " (협의 가능)" : ""}`,
    },
    { icon: MapPin, label: "근무지역", value: posting.workLocation ?? "-" },
    { icon: Clock3, label: "근무시간", value: posting.workHours ?? "-" },
    {
      icon: CalendarDays,
      label: "마감일",
      value: posting.alwaysOpen ? "상시채용" : formatDate(posting.deadlineDate),
    },
  ];

  return (
    <div className="mx-auto w-full max-w-[1400px] space-y-4 px-4 py-8">
      <Link to="/jobs" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="size-4" />
        공고 목록
      </Link>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <Card className="border-slate-200">
        <CardHeader>
          <div className="flex flex-wrap items-center gap-2 text-sm text-slate-500">
            <Building2 className="size-4" />
            <span>{posting.companyName ?? "기업"}</span>
            {posting.trustGrade && posting.trustGrade !== "BASIC" && (
              <Badge className="bg-blue-100 text-blue-700">{TRUST_GRADE_LABELS[posting.trustGrade]}</Badge>
            )}
            <span className="ml-auto flex items-center gap-1 text-xs"><Eye className="size-3.5" />{posting.viewCount.toLocaleString()}</span>
          </div>
          <CardTitle className="text-xl">{posting.title}</CardTitle>
          <div className="flex flex-wrap items-center gap-2 text-sm text-slate-600">
            <Badge className="bg-slate-100 text-slate-700">{posting.jobRole}</Badge>
            <Badge className="bg-slate-100 text-slate-700">{EMPLOYMENT_TYPE_LABELS[posting.employmentType] ?? posting.employmentType}</Badge>
            {posting.headcount && <Badge className="bg-slate-100 text-slate-700">채용인원 {posting.headcount}</Badge>}
          </div>
        </CardHeader>
        <CardContent className="space-y-5">
          {/* 근무 조건 요약 */}
          <div className="grid gap-3 rounded-lg bg-slate-50 p-4 sm:grid-cols-2 lg:grid-cols-3">
            {summaryRows.map(({ icon: Icon, label, value }) => (
              <div key={label} className="flex items-start gap-2 text-sm">
                <Icon className="mt-0.5 size-4 shrink-0 text-slate-400" />
                <div>
                  <div className="text-xs text-slate-500">{label}</div>
                  <div className="text-slate-800">{value}</div>
                </div>
              </div>
            ))}
          </div>

          <Section title="주요업무" body={posting.mainTasks} />
          <Section title="자격요건" body={posting.requirements} />
          <Section title="우대사항" body={posting.preferred} />
          <Section title="복리후생" body={posting.benefits} />
          <Section title="전형절차" body={posting.hiringProcess} />

          {posting.tags.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {posting.tags.map((tag) => (
                <span key={tag} className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">#{tag}</span>
              ))}
            </div>
          )}

          <div className="flex flex-col gap-2 border-t border-slate-100 pt-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs text-slate-500">
              게시일 {formatDate(posting.publishedAt)} · 이 공고 본문으로 지원 건을 만들어 공고 분석과 적합도 분석을 바로 시작할 수 있습니다.
            </p>
            <Button
              className="bg-blue-600 text-white hover:bg-blue-700"
              disabled={analyzing}
              onClick={() => void analyze()}
            >
              <Sparkles className="size-4" />
              {analyzing ? "지원 건 생성 중..." : "이 공고로 분석하기"}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
