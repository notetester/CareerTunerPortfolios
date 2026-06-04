import { useLocation } from "react-router";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { HelpCircle, LifeBuoy, Mail, Megaphone, MessageCircle, Search, ShieldQuestion } from "lucide-react";

const supportSections = {
  "/support": {
    title: "고객센터",
    badge: "Support",
    desc: "서비스 이용 중 필요한 도움, 문의, 공지, FAQ를 한 곳에서 확인합니다.",
    icon: LifeBuoy,
  },
  "/support/guide": {
    title: "사용 가이드",
    badge: "Guide",
    desc: "지원 건 생성부터 공고 분석, 면접, 첨삭, 결제까지 단계별 사용 흐름을 안내합니다.",
    icon: Search,
  },
  "/support/faq": {
    title: "자주 묻는 질문",
    badge: "FAQ",
    desc: "계정, AI 분석, 크레딧, 결제, 데이터 사용과 관련된 질문을 정리합니다.",
    icon: HelpCircle,
  },
  "/support/notices": {
    title: "공지사항",
    badge: "Notice",
    desc: "서비스 업데이트, 점검, 정책 변경, 신규 기능 공지를 제공합니다.",
    icon: Megaphone,
  },
  "/support/contact": {
    title: "문의하기",
    badge: "Contact",
    desc: "오류 제보, 결제 문의, 제휴/도입 문의를 접수하는 창구입니다.",
    icon: Mail,
  },
} as const;

const guideItems = ["프로필 작성", "지원 건 만들기", "공고문 업로드", "AI 분석 확인", "면접 연습", "첨삭 요청", "크레딧 관리"];
const faqItems = [
  "무료 플랜에서 제공되는 분석 횟수는 어떻게 계산되나요?",
  "AI가 생성한 면접 질문을 저장할 수 있나요?",
  "개인정보와 이력서 데이터는 어떻게 보호되나요?",
  "크레딧 환불과 만료 기준은 어떻게 되나요?",
];
const noticeItems = [
  { title: "CareerTuner 베타 서비스 구조 개편 안내", date: "2026-06-05" },
  { title: "AI 데이터 사용 동의 화면 추가 예정", date: "2026-06-03" },
  { title: "개발용 크레딧 정책 안내", date: "2026-06-01" },
];

export function SupportPage() {
  const location = useLocation();
  const section = supportSections[location.pathname as keyof typeof supportSections] ?? supportSections["/support"];
  const Icon = section.icon;

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1100px] space-y-6 px-4 py-10 sm:px-6">
        <section className="rounded-2xl border border-slate-200 bg-white p-6">
          <Badge className="bg-blue-100 text-blue-700">{section.badge}</Badge>
          <h1 className="mt-3 flex items-center gap-2 text-3xl font-black text-slate-900">
            <Icon className="size-7 text-blue-600" />
            {section.title}
          </h1>
          <p className="mt-2 max-w-3xl text-sm leading-relaxed text-slate-600">{section.desc}</p>
        </section>

        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
          <Card className="border border-slate-200 bg-white">
            <CardHeader>
              <CardTitle className="text-base">운영 콘텐츠</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {(location.pathname === "/support/notices" ? noticeItems.map((item) => `${item.date} · ${item.title}`) : location.pathname === "/support/faq" ? faqItems : guideItems).map((item) => (
                <div key={item} className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm font-semibold text-slate-700">
                  {item}
                </div>
              ))}
            </CardContent>
          </Card>

          <div className="space-y-4">
            <Card className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <MessageCircle className="size-4 text-blue-600" />
                  빠른 문의
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <Button className="w-full bg-gradient-to-r from-blue-600 to-indigo-600">문의 접수</Button>
                <Button variant="outline" className="w-full">오류 제보</Button>
              </CardContent>
            </Card>
            <Card className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <ShieldQuestion className="size-4 text-green-600" />
                  관리 대상
                </CardTitle>
              </CardHeader>
              <CardContent className="text-sm leading-relaxed text-slate-600">
                공지, FAQ, 문의 유형, 답변 템플릿은 고객지원 어드민에서 관리하는 구조로 확장합니다.
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
}
