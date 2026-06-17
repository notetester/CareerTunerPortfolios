import { Link, useLocation } from "react-router";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { BarChart3, Briefcase, CheckCircle2, MessageSquare, PenLine, Sparkles, Target, TrendingUp } from "lucide-react";

const featureCards = [
  { title: "공고 분석", href: "/applications?tab=analysis", icon: Briefcase, desc: "공고 원문을 구조화하고 필수/우대 역량과 난이도를 분리합니다." },
  { title: "내 스펙 비교", href: "/applications?tab=fit", icon: Target, desc: "프로필과 공고 요구사항을 비교해 강점과 부족 역량을 보여줍니다." },
  { title: "AI 가상 면접", href: "/interview", icon: MessageSquare, desc: "직무, 인성, 실전, 음성, 아바타 면접 흐름을 제공합니다." },
  { title: "AI 첨삭", href: "/correction", icon: PenLine, desc: "답변, 자기소개서, 이력서, 포트폴리오 설명을 개선합니다." },
  { title: "취업 분석", href: "/analysis", icon: TrendingUp, desc: "여러 지원 건을 종합해 지원 경향과 장기 전략을 제공합니다." },
  { title: "결제/사용량", href: "/billing", icon: BarChart3, desc: "요금제, AI 사용량, 크레딧, 결제 내역을 관리합니다." },
];

export function ServiceInfoPage() {
  const location = useLocation();
  const isFeatures = location.pathname === "/features";

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1200px] space-y-8 px-4 py-10 sm:px-6">
        <section className="space-y-4">
          <Badge className="bg-blue-100 text-blue-700">{isFeatures ? "기능 소개" : "서비스 소개"}</Badge>
          <h1 className="max-w-3xl text-3xl font-black leading-tight text-slate-900">
            CareerTuner는 지원 건 하나를 중심으로 공고 분석부터 면접 준비까지 이어지는 취업 전략 작업공간입니다
          </h1>
          <p className="max-w-3xl text-sm leading-relaxed text-slate-600">
            단순한 채용공고 저장소가 아니라 기업/직무별 지원 건에 AI 분석, 스펙 비교, 지원 전략, 예상 질문, 모의면접, 첨삭, 장기 분석 기록을 쌓는 구조입니다.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button asChild className="bg-primary">
              <Link to="/applications?tab=new">지원 건 만들기</Link>
            </Button>
            <Button asChild variant="outline">
              <Link to="/support/guide">사용 가이드 보기</Link>
            </Button>
          </div>
        </section>

        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {featureCards.map((feature) => (
            <Link key={feature.title} to={feature.href}>
              <Card className="h-full border border-slate-200 bg-white transition-all hover:border-blue-300 hover:shadow-md">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2 text-base">
                    <feature.icon className="size-5 text-blue-600" />
                    {feature.title}
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-sm leading-relaxed text-slate-600">{feature.desc}</p>
                </CardContent>
              </Card>
            </Link>
          ))}
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-6">
          <h2 className="text-lg font-black text-slate-900">서비스 운영 구조</h2>
          <div className="mt-4 grid gap-3 md:grid-cols-3">
            {["지원 건 중심 데이터", "AI 사용량/크레딧 추적", "관리자 콘텐츠 운영"].map((item) => (
              <div key={item} className="flex items-start gap-2 rounded-xl bg-slate-50 p-4 text-sm text-slate-700">
                <CheckCircle2 className="mt-0.5 size-4 text-green-600" />
                {item}
              </div>
            ))}
          </div>
        </section>

        <div className="flex items-center gap-2 text-sm text-slate-500">
          <Sparkles className="size-4 text-blue-600" />
          서비스 상세 정책과 콘텐츠는 어드민 기능군에서 관리될 예정입니다.
        </div>
      </div>
    </div>
  );
}
