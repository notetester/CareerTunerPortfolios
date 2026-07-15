import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router";
import { ArrowRight, Briefcase, Compass, FilePlus2, GraduationCap, Loader2, RefreshCw, Star, Target } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { listApplicationCases } from "../api/applicationCasesApi";
import { getApplicationStatusLabel, type ApplicationCase, type ApplicationStatus } from "../types/applicationCase";

const statusOrder: ApplicationStatus[] = ["DRAFT", "ANALYZING", "READY", "APPLIED", "CLOSED"];

const destinations = [
  { title: "전체 지원 건 목록", description: "검색·상태 필터·보관 기능으로 지원 건을 관리합니다.", href: "/applications/list", icon: Briefcase },
  { title: "새 지원 건 만들기", description: "공고 텍스트나 파일을 등록해 새 준비 공간을 만듭니다.", href: "/applications/new", icon: FilePlus2 },
  { title: "내 스펙과 비교", description: "지원 건별 적합도와 강점·부족 역량을 비교합니다.", href: "/applications/compare", icon: Target },
  { title: "지원 전략", description: "지원 건별 강조 경험과 실행 계획을 한곳에서 확인합니다.", href: "/applications/strategy", icon: Compass },
  { title: "학습·자격증 추천", description: "부족 역량을 보완할 학습 과제와 자격 정보를 확인합니다.", href: "/applications/learning", icon: GraduationCap },
] as const;

/** 지원 건 관리 대분류 전용 허브. 목록 페이지와 분리해 상태 요약과 각 기능 진입점을 제공한다. */
export function ApplicationHubPage() {
  const [cases, setCases] = useState<ApplicationCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCases(await listApplicationCases(true));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "지원 건 현황을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const activeCases = useMemo(() => cases.filter((item) => !item.archived && !item.deletedAt), [cases]);
  const archivedCount = useMemo(() => cases.filter((item) => item.archived && !item.deletedAt).length, [cases]);
  const recent = useMemo(() => [...activeCases].sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt)).slice(0, 5), [activeCases]);
  const counts = useMemo(() => Object.fromEntries(statusOrder.map((status) => [status, activeCases.filter((item) => item.status === status).length])) as Record<ApplicationStatus, number>, [activeCases]);

  return (
    <main className="min-h-[calc(100vh-72px)] bg-background">
      <div className="mx-auto w-full max-w-[1440px] space-y-6 px-4 py-8 sm:px-6 lg:px-8">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div><h1 className="flex items-center gap-2 text-2xl font-black text-foreground"><Briefcase className="size-6 text-primary" />지원 건 관리</h1><p className="mt-1 text-sm text-muted-foreground">지원 준비 현황을 요약해서 보고 목록·등록·분석 기능으로 바로 이동합니다.</p></div>
          <div className="flex gap-2"><Button variant="outline" onClick={() => void load()} disabled={loading}><RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />새로고침</Button><Button asChild><Link to="/applications/new"><FilePlus2 className="size-4" />새 지원 건</Link></Button></div>
        </div>

        {error && <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</div>}

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7">
          <Metric label="활성 지원 건" value={activeCases.length} loading={loading} emphasis />
          {statusOrder.map((status) => <Metric key={status} label={getApplicationStatusLabel(status)} value={counts[status]} loading={loading} />)}
          <Metric label="보관" value={archivedCount} loading={loading} />
        </div>

        <div className="grid gap-5 xl:grid-cols-[minmax(0,1.15fr)_minmax(420px,0.85fr)]">
          <section>
            <h2 className="mb-3 text-lg font-bold text-foreground">지원 기능</h2>
            <div className="grid gap-3 md:grid-cols-2">
              {destinations.map((item) => <Link key={item.href} to={item.href} className="group rounded-xl border border-border bg-card p-5 shadow-[var(--shadow-card)] transition hover:-translate-y-0.5 hover:border-primary/50">
                <div className="flex items-start justify-between gap-3"><span className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary"><item.icon className="size-5" /></span><ArrowRight className="size-4 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-primary" /></div>
                <h3 className="mt-4 font-bold text-foreground group-hover:text-primary">{item.title}</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">{item.description}</p>
              </Link>)}
            </div>
          </section>

          <section>
            <div className="mb-3 flex items-center justify-between"><h2 className="text-lg font-bold text-foreground">최근 활동</h2><Button asChild variant="ghost" size="sm"><Link to="/applications/list">전체 목록<ArrowRight className="size-4" /></Link></Button></div>
            <Card className="border-border"><CardContent className="p-0">
              {loading ? <div className="flex min-h-72 items-center justify-center gap-2 text-sm text-muted-foreground"><Loader2 className="size-5 animate-spin" />지원 건을 불러오는 중입니다.</div> : recent.length ? <div className="divide-y divide-border">{recent.map((item) => <Link key={item.id} to={`/applications/${item.id}`} className="flex items-center gap-3 p-4 transition hover:bg-accent"><span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"><Briefcase className="size-4" /></span><div className="min-w-0 flex-1"><div className="flex items-center gap-2"><span className="truncate font-semibold text-foreground">{item.companyName}</span>{item.favorite && <Star className="size-3.5 fill-amber-400 text-amber-400" />}</div><div className="mt-0.5 truncate text-xs text-muted-foreground">{item.jobTitle} · {formatRelative(item.updatedAt)}</div></div><Badge variant="secondary">{getApplicationStatusLabel(item.status)}</Badge></Link>)}</div> : <div className="flex min-h-72 flex-col items-center justify-center px-6 text-center"><FilePlus2 className="size-8 text-muted-foreground" /><div className="mt-3 font-semibold text-foreground">아직 지원 건이 없습니다</div><p className="mt-1 text-sm text-muted-foreground">첫 공고를 등록하면 진행 현황이 이곳에 표시됩니다.</p><Button asChild className="mt-4"><Link to="/applications/new">첫 지원 건 만들기</Link></Button></div>}
            </CardContent></Card>
          </section>
        </div>
      </div>
    </main>
  );
}

function Metric({ label, value, loading, emphasis = false }: { label: string; value: number; loading: boolean; emphasis?: boolean }) {
  return <Card className={`border-border ${emphasis ? "bg-primary text-primary-foreground" : "bg-card"}`}><CardHeader className="gap-2 px-4 pt-4"><CardTitle className={`text-xs font-semibold ${emphasis ? "text-primary-foreground/80" : "text-muted-foreground"}`}>{label}</CardTitle><div className="text-2xl font-black">{loading ? <Loader2 className="size-5 animate-spin" /> : value.toLocaleString("ko-KR")}</div></CardHeader></Card>;
}

function formatRelative(value: string): string {
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return value;
  const diffMinutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60_000));
  if (diffMinutes < 1) return "방금 전";
  if (diffMinutes < 60) return `${diffMinutes}분 전`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간 전`;
  const diffDays = Math.floor(diffHours / 24);
  return diffDays < 30 ? `${diffDays}일 전` : new Date(timestamp).toLocaleDateString("ko-KR");
}
