import { useLocation, useSearchParams } from "react-router";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Building2, Newspaper, Radio, Rocket, Users } from "lucide-react";

const companySections = {
  "/company/about": { title: "서비스 소개", badge: "About", icon: Building2, desc: "CareerTuner의 서비스 목적, 제공 가치, 운영 방향을 소개합니다." },
  "/company/team": { title: "팀 소개", badge: "Team", icon: Users, desc: "제품, AI, 백엔드, 프론트엔드, 디자인, 운영 팀 구성을 소개합니다." },
  "/company/careers": { title: "채용", badge: "Careers", icon: Rocket, desc: "CareerTuner 팀과 함께할 포지션과 채용 절차를 안내합니다." },
  "/company/blog": { title: "블로그", badge: "Blog", icon: Newspaper, desc: "취업 준비, AI 면접, 제품 업데이트 관련 글을 제공합니다." },
  "/company/press": { title: "보도자료", badge: "Press", icon: Radio, desc: "공식 보도자료와 미디어 키트를 제공합니다." },
  "/company/social": { title: "공식 채널", badge: "Social", icon: Radio, desc: "CareerTuner의 공식 SNS와 커뮤니케이션 채널을 정리합니다." },
} as const;

export function CompanyPage() {
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const section = companySections[location.pathname as keyof typeof companySections] ?? companySections["/company/about"];
  const Icon = section.icon;
  const channel = searchParams.get("channel");

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1100px] space-y-6 px-4 py-10 sm:px-6">
        <section className="rounded-2xl border border-slate-200 bg-white p-6">
          <Badge className="bg-indigo-100 text-indigo-700">{section.badge}</Badge>
          <h1 className="mt-3 flex items-center gap-2 text-3xl font-black text-slate-900">
            <Icon className="size-7 text-indigo-600" />
            {section.title}
          </h1>
          <p className="mt-2 max-w-3xl text-sm leading-relaxed text-slate-600">{section.desc}</p>
          {channel && <div className="mt-3 text-sm font-semibold text-indigo-700">선택 채널: {channel}</div>}
        </section>

        <div className="grid gap-4 md:grid-cols-3">
          {["콘텐츠 관리", "공개 상태", "어드민 승인"].map((item) => (
            <Card key={item} className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="text-base">{item}</CardTitle>
              </CardHeader>
              <CardContent className="text-sm text-slate-600">
                회사/콘텐츠 기능군의 관리자 화면에서 작성, 검수, 게시 흐름을 관리합니다.
              </CardContent>
            </Card>
          ))}
        </div>

        <Button variant="outline">콘텐츠 초안 보기</Button>
      </div>
    </div>
  );
}
