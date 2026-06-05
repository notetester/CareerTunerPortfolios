import { Link, useLocation } from "react-router";
import { Badge } from "../../app/components/ui/badge";
import { Button } from "../../app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../../app/components/ui/card";
import { Progress } from "../../app/components/ui/progress";
import {
  AlertTriangle,
  BarChart3,
  Bell,
  Bot,
  CreditCard,
  FileText,
  Gauge,
  MessageSquareWarning,
  ReceiptText,
  ScrollText,
  ShieldCheck,
  Users,
} from "lucide-react";

const adminSections = [
  {
    label: "회원 관리",
    href: "/admin/users",
    icon: Users,
    desc: "회원 상태, 권한, 이메일 인증, 요금제 정보를 확인합니다.",
    count: "1,248",
  },
  {
    label: "결제 관리",
    href: "/admin/payments",
    icon: CreditCard,
    desc: "결제 내역, 크레딧 충전, 환불 대기 건을 관리합니다.",
    count: "34",
  },
  {
    label: "AI 사용량 관리",
    href: "/admin/ai-usage",
    icon: Gauge,
    desc: "기능별 토큰 사용량, 크레딧 차감, 오류율을 추적합니다.",
    count: "8.2M",
  },
  {
    label: "게시판/신고 관리",
    href: "/admin/community",
    icon: MessageSquareWarning,
    desc: "커뮤니티 게시글, 댓글, 신고 글, 익명 처리 요청을 봅니다.",
    count: "12",
  },
  {
    label: "공지사항 관리",
    href: "/admin/notices",
    icon: Bell,
    desc: "공지사항, FAQ, 사용 가이드, 고객센터 콘텐츠를 운영합니다.",
    count: "7",
  },
  {
    label: "요금제 관리",
    href: "/admin/plans",
    icon: ReceiptText,
    desc: "무료/베이직/프로 플랜과 기능별 크레딧 정책을 조정합니다.",
    count: "4",
  },
  {
    label: "프롬프트 템플릿",
    href: "/admin/prompts",
    icon: Bot,
    desc: "공고 분석, 질문 생성, 답변 평가 프롬프트를 관리합니다.",
    count: "18",
  },
  {
    label: "시스템 로그",
    href: "/admin/logs",
    icon: ScrollText,
    desc: "관리자 작업 로그, API 오류, 배치 실행 기록을 확인합니다.",
    count: "256",
  },
];

const statusCards = [
  { label: "전체 회원", value: "1,248", sub: "오늘 가입 18명", icon: Users, tone: "text-blue-600", bg: "bg-blue-50" },
  { label: "이번 달 매출", value: "₩4.8M", sub: "전월 대비 +12%", icon: CreditCard, tone: "text-emerald-600", bg: "bg-emerald-50" },
  { label: "AI 사용량", value: "8.2M", sub: "토큰 기준", icon: BarChart3, tone: "text-purple-600", bg: "bg-purple-50" },
  { label: "처리 대기", value: "19", sub: "신고 12 · 문의 7", icon: AlertTriangle, tone: "text-amber-600", bg: "bg-amber-50" },
];

const promptHealth = [
  { label: "공고 분석 프롬프트", version: "v0.3", score: 82 },
  { label: "면접 질문 생성", version: "v0.2", score: 76 },
  { label: "답변 평가/첨삭", version: "v0.4", score: 88 },
  { label: "장기 취업 경향 분석", version: "초안", score: 54 },
];

const routeLabels: Record<string, string> = {
  "/admin": "관리자 대시보드",
  "/admin/users": "회원 관리",
  "/admin/payments": "결제 관리",
  "/admin/ai-usage": "AI 사용량 관리",
  "/admin/community": "게시판/신고 관리",
  "/admin/notices": "공지사항 관리",
  "/admin/plans": "요금제 관리",
  "/admin/prompts": "프롬프트 템플릿",
  "/admin/logs": "시스템 로그",
};

export function AdminDashboardPage() {
  const location = useLocation();
  const currentLabel = routeLabels[location.pathname] ?? "관리자 대시보드";

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6">
        <section className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Badge className="mb-3 bg-slate-900 text-white">Admin</Badge>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <ShieldCheck className="size-6 text-blue-600" />
              {currentLabel}
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              회원, 결제, AI 사용량, 콘텐츠, 프롬프트를 한 화면에서 점검하는 관리자 작업 공간입니다.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline">
              <Link to="/admin/logs">작업 로그</Link>
            </Button>
            <Button asChild className="bg-gradient-to-r from-blue-600 to-indigo-600">
              <Link to="/admin/prompts">프롬프트 점검</Link>
            </Button>
          </div>
        </section>

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {statusCards.map((card) => (
            <Card key={card.label} className="border border-slate-200 bg-white">
              <CardContent className="p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="text-sm text-slate-500">{card.label}</div>
                    <div className="mt-1 text-3xl font-black text-slate-900">{card.value}</div>
                    <div className="mt-1 text-xs text-slate-400">{card.sub}</div>
                  </div>
                  <div className={`flex size-10 items-center justify-center rounded-xl ${card.bg}`}>
                    <card.icon className={`size-5 ${card.tone}`} />
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </section>

        <section className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-900">관리 기능</h2>
              <Badge className="bg-blue-100 text-blue-700">초기 운영 스켈레톤</Badge>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              {adminSections.map((section) => (
                <Link key={section.href} to={section.href}>
                  <Card className="h-full border border-slate-200 bg-white transition-all hover:border-blue-300 hover:shadow-md">
                    <CardContent className="p-5">
                      <div className="flex items-start gap-3">
                        <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-slate-100">
                          <section.icon className="size-5 text-blue-600" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center justify-between gap-3">
                            <h3 className="font-bold text-slate-900">{section.label}</h3>
                            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600">
                              {section.count}
                            </span>
                          </div>
                          <p className="mt-2 text-sm leading-relaxed text-slate-500">{section.desc}</p>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                </Link>
              ))}
            </div>
          </div>

          <aside className="space-y-4">
            <Card className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <Bot className="size-4 text-purple-600" />
                  프롬프트 상태
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {promptHealth.map((prompt) => (
                  <div key={prompt.label} className="space-y-1.5">
                    <div className="flex items-center justify-between gap-3 text-sm">
                      <span className="font-semibold text-slate-700">{prompt.label}</span>
                      <span className="text-xs text-slate-400">{prompt.version}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Progress value={prompt.score} className="h-2 flex-1" />
                      <span className="w-9 text-right text-xs font-bold text-blue-600">{prompt.score}</span>
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>

            <Card className="border border-amber-200 bg-amber-50">
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base text-amber-900">
                  <AlertTriangle className="size-4 text-amber-600" />
                  운영 대기 항목
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm text-amber-800">
                {[
                  "신고 게시글 12건 검토 필요",
                  "미처리 환불 요청 3건",
                  "프롬프트 초안 2개 승인 대기",
                  "AI 사용량 급증 사용자 4명 확인",
                ].map((item) => (
                  <div key={item} className="rounded-lg bg-white/70 px-3 py-2 font-medium">
                    {item}
                  </div>
                ))}
              </CardContent>
            </Card>

            <Card className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <FileText className="size-4 text-slate-600" />
                  최근 관리자 작업
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {[
                  "FAQ 3건 게시 상태 변경",
                  "프로 플랜 크레딧 정책 검토",
                  "면접 질문 생성 프롬프트 v0.2 적용",
                  "신규 관리자 계정 권한 확인",
                ].map((item) => (
                  <div key={item} className="border-b border-slate-100 pb-3 text-sm text-slate-600 last:border-b-0 last:pb-0">
                    {item}
                  </div>
                ))}
              </CardContent>
            </Card>
          </aside>
        </section>
      </div>
    </div>
  );
}
