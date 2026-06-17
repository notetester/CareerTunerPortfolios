import { useSearchParams } from "react-router";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Progress } from "../components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { AlertTriangle, Award, BarChart3, CheckCircle2, CreditCard, ReceiptText, Zap } from "lucide-react";

const tabs = ["plans", "usage", "credits", "history"] as const;
type BillingTab = (typeof tabs)[number];

const plans = [
  { name: "무료", price: "0원", desc: "기본 체험", features: ["공고 분석 월 3회", "텍스트 면접 월 1회", "지원 건 3건 저장"] },
  { name: "베이직", price: "9,900원", desc: "가벼운 취업 준비", features: ["공고 분석 월 20회", "텍스트 면접 무제한", "답변 첨삭 월 5회"] },
  { name: "프로", price: "29,000원", desc: "집중 취업 준비", features: ["공고 분석 무제한", "음성 면접", "장기 취업 분석"], popular: true },
  { name: "프리미엄", price: "49,000원", desc: "고급 면접 패키지", features: ["아바타 면접관", "영상/자세 분석", "1:1 전략 컨설팅"] },
];

const usageRows = [
  { feature: "공고문 분석", used: 8, limit: 20, credit: 1 },
  { feature: "예상 질문 생성", used: 12, limit: 40, credit: 1 },
  { feature: "텍스트 모의면접", used: 5, limit: 999, credit: 2 },
  { feature: "음성 모의면접", used: 2, limit: 10, credit: 3 },
  { feature: "자기소개서 첨삭", used: 3, limit: 10, credit: 2 },
];

const creditPacks = [
  { amount: 10, price: "4,900원" },
  { amount: 30, price: "11,900원", badge: "인기" },
  { amount: 50, price: "18,000원" },
  { amount: 100, price: "29,000원", badge: "최저가" },
];

const payments = [
  { date: "2026-06-01", item: "프로 플랜 월간 구독", amount: "29,000원", status: "결제 완료" },
  { date: "2026-05-20", item: "크레딧 30개", amount: "11,900원", status: "결제 완료" },
  { date: "2026-05-01", item: "베이직 플랜 월간 구독", amount: "9,900원", status: "결제 완료" },
];

export function BillingPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab") ?? "plans";
  const activeTab: BillingTab = tabs.includes(requestedTab as BillingTab) ? (requestedTab as BillingTab) : "plans";

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
            <CreditCard className="size-6 text-blue-600" />
            결제/구독
          </h1>
          <p className="mt-1 text-sm text-slate-500">요금제, AI 사용량, 크레딧 충전, 결제 내역을 한 곳에서 관리합니다</p>
        </div>

        <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          <AlertTriangle className="mt-0.5 size-5 shrink-0" />
          <div>
            <div className="font-bold">결제/크레딧 API 준비 중</div>
            <p className="mt-1 leading-6">현재 표시는 샘플 데이터입니다. 플랜 변경, 크레딧 구매, 결제 내역 조회는 백엔드 구현 후 활성화됩니다.</p>
          </div>
        </div>

        <Tabs value={activeTab} onValueChange={(value) => setSearchParams({ tab: value })}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-white p-1">
            <TabsTrigger value="plans">요금제</TabsTrigger>
            <TabsTrigger value="usage">AI 사용량</TabsTrigger>
            <TabsTrigger value="credits">크레딧 충전</TabsTrigger>
            <TabsTrigger value="history">결제 내역</TabsTrigger>
          </TabsList>

          <TabsContent value="plans" className="mt-5">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {plans.map((plan) => (
                <Card key={plan.name} className={`relative border-2 bg-white ${plan.popular ? "border-blue-500 shadow-lg" : "border-slate-200"}`}>
                  {plan.popular && <Badge className="absolute -top-3 left-1/2 -translate-x-1/2 bg-blue-600 text-white">추천</Badge>}
                  <CardHeader>
                    <CardTitle className="text-lg">{plan.name} 플랜</CardTitle>
                    <p className="text-sm text-slate-500">{plan.desc}</p>
                    <div className="pt-2 text-3xl font-black text-slate-900">{plan.price}</div>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="space-y-2">
                      {plan.features.map((feature) => (
                        <div key={feature} className="flex items-center gap-2 text-sm text-slate-700">
                          <CheckCircle2 className="size-4 text-green-600" />
                          {feature}
                        </div>
                      ))}
                    </div>
                    <Button disabled className={plan.popular ? "w-full bg-gradient-to-r from-blue-600 to-indigo-600" : "w-full"} variant={plan.popular ? "default" : "outline"}>
                      준비 중
                    </Button>
                  </CardContent>
                </Card>
              ))}
            </div>
          </TabsContent>

          <TabsContent value="usage" className="mt-5">
            <div className="grid gap-4 lg:grid-cols-3">
              <Card className="border border-slate-200 bg-white lg:col-span-2">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2 text-base">
                    <BarChart3 className="size-4 text-blue-600" />
                    이번 달 AI 사용량
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  {usageRows.map((row) => {
                    const pct = row.limit === 999 ? 36 : Math.round((row.used / row.limit) * 100);
                    return (
                      <div key={row.feature} className="space-y-1.5">
                        <div className="flex items-center justify-between text-sm">
                          <span className="font-semibold text-slate-700">{row.feature}</span>
                          <span className="text-xs text-slate-500">{row.limit === 999 ? `${row.used}회 사용` : `${row.used}/${row.limit}회`}</span>
                        </div>
                        <Progress value={pct} className="h-2" />
                      </div>
                    );
                  })}
                </CardContent>
              </Card>
              <Card className="border border-slate-200 bg-white">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2 text-base">
                    <Award className="size-4 text-amber-600" />
                    보유 크레딧
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="rounded-xl bg-amber-50 p-5 text-center">
                    <div className="text-4xl font-black text-amber-600">10</div>
                    <div className="text-sm text-amber-700">사용 가능 크레딧</div>
                  </div>
                  <Button disabled className="w-full bg-gradient-to-r from-amber-500 to-orange-500" onClick={() => setSearchParams({ tab: "credits" })}>
                    충전하러 가기
                  </Button>
                </CardContent>
              </Card>
            </div>
          </TabsContent>

          <TabsContent value="credits" className="mt-5">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {creditPacks.map((pack) => (
                <Card key={pack.amount} className={`relative border-2 bg-white ${pack.badge ? "border-amber-400 shadow-lg" : "border-slate-200"}`}>
                  {pack.badge && <Badge className="absolute -top-3 left-1/2 -translate-x-1/2 bg-amber-500 text-white">{pack.badge}</Badge>}
                  <CardContent className="space-y-4 p-5 text-center">
                    <Zap className="mx-auto size-8 text-amber-500" />
                    <div>
                      <div className="text-4xl font-black text-slate-900">{pack.amount}</div>
                      <div className="text-sm text-slate-500">크레딧</div>
                    </div>
                    <div className="text-xl font-black text-blue-600">{pack.price}</div>
                    <Button disabled className="w-full">준비 중</Button>
                  </CardContent>
                </Card>
              ))}
            </div>
          </TabsContent>

          <TabsContent value="history" className="mt-5">
            <Card className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <ReceiptText className="size-4 text-slate-600" />
                  결제 내역
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {payments.map((payment) => (
                  <div key={`${payment.date}-${payment.item}`} className="flex flex-col gap-2 rounded-xl border border-slate-200 bg-slate-50 p-4 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <div className="text-sm font-semibold text-slate-800">{payment.item}</div>
                      <div className="text-xs text-slate-500">{payment.date}</div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-black text-slate-900">{payment.amount}</span>
                      <Badge className="bg-slate-200 text-slate-700">샘플 · {payment.status}</Badge>
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
