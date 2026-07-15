import { Link } from "react-router";
import { ArrowRight, BarChart3, BriefcaseBusiness, FileSearch, MessageSquare, ShieldCheck, Sparkles } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";

const serviceFlow = [
  {
    step: "01",
    title: "지원 건 만들기",
    description: "공고 원문·URL·파일을 등록하고 추출된 회사, 직무, 요구 역량을 직접 검수합니다.",
    href: "/job-analysis",
    icon: FileSearch,
  },
  {
    step: "02",
    title: "내 자료와 비교하기",
    description: "프로필과 공고 요구조건을 비교해 적합도, 부족 역량, 학습 우선순위를 확인합니다.",
    href: "/applications/compare",
    icon: BriefcaseBusiness,
  },
  {
    step: "03",
    title: "면접과 문서 준비하기",
    description: "지원 건 맥락을 이어받아 예상 질문, 모의면접, 답변·자기소개서 첨삭을 실행합니다.",
    href: "/interview",
    icon: MessageSquare,
  },
  {
    step: "04",
    title: "준비 흐름 점검하기",
    description: "지원 경향과 준비도를 분석하고 필요한 작업을 플래너 일정으로 옮깁니다.",
    href: "/analysis",
    icon: BarChart3,
  },
];

export function ServiceAboutPage() {
  return (
    <main className="min-h-screen bg-background text-foreground">
      <div className="mx-auto w-full max-w-[1400px] space-y-12 px-4 py-10 sm:px-6 lg:px-8">
        <section className="grid gap-8 rounded-3xl border border-border bg-card p-7 shadow-sm lg:grid-cols-[minmax(0,1.3fr)_minmax(320px,0.7fr)] lg:p-10">
          <div>
            <Badge className="bg-primary/10 text-primary">서비스 소개</Badge>
            <h1 className="mt-5 max-w-4xl text-3xl font-black leading-tight sm:text-5xl">
              공고 하나가 아니라, 실제 지원 준비의 전체 맥락을 연결합니다
            </h1>
            <p className="mt-5 max-w-3xl text-base leading-8 text-muted-foreground">
              CareerTuner는 지원 건을 중심으로 공고 분석, 프로필 비교, 준비 전략, 면접, 첨삭과 일정을 연결합니다.
              각 기능은 독립된 화면에서 동작하고 필요한 순간에는 같은 지원 건 데이터를 이어받습니다.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Button asChild><Link to="/job-analysis">공고 분석 시작 <ArrowRight className="size-4" /></Link></Button>
              <Button asChild variant="outline"><Link to="/features">전체 기능 보기</Link></Button>
            </div>
          </div>
          <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-1">
            {[
              ["지원 건 중심", "공고·프로필·AI 결과를 기업과 직무별로 분리"],
              ["사용자 검수", "AI가 추출한 원문과 결과를 저장 전에 확인"],
              ["동의 기반", "AI 데이터와 이력서 분석 범위를 사용자가 통제"],
            ].map(([title, body]) => (
              <div key={title} className="rounded-2xl border border-border bg-muted/40 p-5">
                <div className="flex items-center gap-2 font-bold"><ShieldCheck className="size-4 text-primary" />{title}</div>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">{body}</p>
              </div>
            ))}
          </div>
        </section>

        <section>
          <div className="flex items-end justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 text-sm font-bold text-primary"><Sparkles className="size-4" />서비스 이용 흐름</div>
              <h2 className="mt-2 text-2xl font-black">독립된 기능을 하나의 지원 준비 흐름으로 사용하세요</h2>
            </div>
          </div>
          <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {serviceFlow.map((item) => (
              <Card key={item.href} className="flex h-full flex-col border-border bg-card">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-black tracking-[0.18em] text-primary">STEP {item.step}</span>
                    <item.icon className="size-5 text-muted-foreground" />
                  </div>
                  <CardTitle className="pt-3 text-lg">{item.title}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-1 flex-col">
                  <p className="flex-1 text-sm leading-6 text-muted-foreground">{item.description}</p>
                  <Button asChild variant="ghost" className="mt-5 justify-start px-0 text-primary hover:bg-transparent">
                    <Link to={item.href}>기능 열기 <ArrowRight className="size-4" /></Link>
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>
      </div>
    </main>
  );
}
