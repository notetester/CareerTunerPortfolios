import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Progress } from "../components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { Award, BarChart3, CheckCircle2, CreditCard, Loader2, ReceiptText, Zap } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import {
  cancelSubscription, getCreditProducts, getMonthlyUsage, getMyBilling, getMyPayments, getPlans,
  purchaseCredits, subscribe,
  type CreditProduct, type MyBilling, type Payment, type SubscriptionPlan, type UsageRow,
} from "@/features/billing/api/billingApi";

const tabs = ["plans", "usage", "credits", "history"] as const;
type BillingTab = (typeof tabs)[number];

// 요금제 기능 설명(마케팅 카피)은 코드별로 클라이언트에 둔다.
const PLAN_FEATURES: Record<string, string[]> = {
  FREE: ["공고 분석 월 3회", "텍스트 면접 월 1회", "지원 건 3건 저장"],
  BASIC: ["공고 분석 월 20회", "텍스트 면접 무제한", "답변 첨삭 월 5회"],
  PRO: ["공고 분석 무제한", "음성 면접", "장기 취업 분석"],
  PREMIUM: ["아바타 면접관", "영상/자세 분석", "1:1 전략 컨설팅"],
};

const FEATURE_LABEL: Record<string, string> = {
  JOB_POSTING_METADATA: "공고문 분석",
  JOB_ANALYSIS: "공고 분석",
  COMPANY_ANALYSIS: "기업 분석",
  FIT_ANALYSIS: "적합도 분석",
  INTERVIEW_QUESTION: "예상 질문 생성",
  INTERVIEW_ANSWER_EVAL: "면접 답변 평가",
  INTERVIEW_REPORT: "면접 리포트",
  CAREER_TREND: "장기 취업 분석",
  DASHBOARD_SUMMARY: "대시보드 요약",
  PROFILE_SUMMARY: "프로필 요약",
  PROFILE_SKILL_EXTRACT: "기술 추출",
  PROFILE_COMPLETENESS: "프로필 완성도",
};

const won = (n: number) => `${n.toLocaleString("ko-KR")}원`;

export function BillingPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab") ?? "plans";
  const activeTab: BillingTab = tabs.includes(requestedTab as BillingTab) ? (requestedTab as BillingTab) : "plans";
  const { isAuthenticated } = useAuth();

  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [products, setProducts] = useState<CreditProduct[]>([]);
  const [billing, setBilling] = useState<MyBilling | null>(null);
  const [usage, setUsage] = useState<UsageRow[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadPublic = async () => {
    const [p, c] = await Promise.all([getPlans(), getCreditProducts()]);
    setPlans(p);
    setProducts(c);
  };

  const loadMine = async () => {
    if (!isAuthenticated) return;
    const [me, u, pay] = await Promise.all([getMyBilling(), getMonthlyUsage(), getMyPayments()]);
    setBilling(me);
    setUsage(u);
    setPayments(pay);
  };

  useEffect(() => {
    void loadPublic().catch(() => setError("요금제 정보를 불러오지 못했습니다."));
    void loadMine().catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);

  const doSubscribe = async (planCode: string) => {
    setBusy(`sub-${planCode}`);
    setError(null);
    try {
      setBilling(await subscribe(planCode, "MONTHLY"));
      await loadMine();
    } catch {
      setError("구독 처리에 실패했습니다.");
    } finally {
      setBusy(null);
    }
  };

  const doCancel = async () => {
    setBusy("cancel");
    try {
      setBilling(await cancelSubscription());
      await loadMine();
    } finally {
      setBusy(null);
    }
  };

  const doPurchase = async (productCode: string) => {
    setBusy(`buy-${productCode}`);
    setError(null);
    try {
      setBilling(await purchaseCredits(productCode));
      await loadMine();
    } catch {
      setError("크레딧 충전에 실패했습니다.");
    } finally {
      setBusy(null);
    }
  };

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

        {billing && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-blue-200 bg-blue-50 p-4">
            <div className="text-sm text-blue-900">
              현재 플랜 <strong>{billing.currentPlanName}</strong>
              {billing.subscriptionStatus === "ACTIVE" && billing.periodEnd && (
                <span className="ml-2 text-xs text-blue-700">~ {billing.periodEnd.slice(0, 10)}</span>
              )}
              <span className="ml-3">보유 크레딧 <strong>{billing.creditBalance}</strong>개</span>
            </div>
            {billing.subscriptionStatus === "ACTIVE" && (
              <Button size="sm" variant="outline" disabled={busy === "cancel"} onClick={() => void doCancel()}>
                {busy === "cancel" && <Loader2 className="size-3.5 animate-spin" />} 구독 해지
              </Button>
            )}
          </div>
        )}

        {!isAuthenticated && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            로그인하면 내 구독 상태, AI 사용량, 결제 내역을 확인하고 요금제·크레딧을 구매할 수 있습니다.
          </div>
        )}
        {error && <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div>}

        <Tabs value={activeTab} onValueChange={(value) => setSearchParams({ tab: value })}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-white p-1">
            <TabsTrigger value="plans">요금제</TabsTrigger>
            <TabsTrigger value="usage">AI 사용량</TabsTrigger>
            <TabsTrigger value="credits">크레딧 충전</TabsTrigger>
            <TabsTrigger value="history">결제 내역</TabsTrigger>
          </TabsList>

          <TabsContent value="plans" className="mt-5">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {plans.map((plan) => {
                const isCurrent = billing?.currentPlanCode === plan.code;
                const popular = plan.code === "PRO";
                return (
                  <Card key={plan.code} className={`relative border-2 bg-white ${popular ? "border-blue-500 shadow-lg" : "border-slate-200"}`}>
                    {popular && <Badge className="absolute -top-3 left-1/2 -translate-x-1/2 bg-blue-600 text-white">추천</Badge>}
                    <CardHeader>
                      <CardTitle className="text-lg">{plan.name} 플랜</CardTitle>
                      <p className="text-sm text-slate-500">{plan.description}</p>
                      <div className="pt-2 text-3xl font-black text-slate-900">{won(plan.monthlyPrice)}</div>
                      {plan.yearlyPrice > 0 && <div className="text-xs text-slate-400">연간 {won(plan.yearlyPrice)}/월</div>}
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <div className="space-y-2">
                        {(PLAN_FEATURES[plan.code] ?? []).map((feature) => (
                          <div key={feature} className="flex items-center gap-2 text-sm text-slate-700">
                            <CheckCircle2 className="size-4 text-green-600" />
                            {feature}
                          </div>
                        ))}
                      </div>
                      <Button
                        disabled={!isAuthenticated || isCurrent || busy === `sub-${plan.code}`}
                        className={popular ? "w-full bg-primary" : "w-full"}
                        variant={popular ? "default" : "outline"}
                        onClick={() => void doSubscribe(plan.code)}
                      >
                        {busy === `sub-${plan.code}` && <Loader2 className="size-4 animate-spin" />}
                        {isCurrent ? "현재 플랜" : plan.code === "FREE" ? "무료 사용" : "이 플랜 구독"}
                      </Button>
                    </CardContent>
                  </Card>
                );
              })}
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
                  {usage.length > 0 ? (
                    usage.map((row) => {
                      const max = Math.max(...usage.map((r) => r.used), 1);
                      return (
                        <div key={row.featureType} className="space-y-1.5">
                          <div className="flex items-center justify-between text-sm">
                            <span className="font-semibold text-slate-700">{FEATURE_LABEL[row.featureType] ?? row.featureType}</span>
                            <span className="text-xs text-slate-500">{row.used}회 · 크레딧 {row.creditUsed}</span>
                          </div>
                          <Progress value={Math.round((row.used / max) * 100)} className="h-2" />
                        </div>
                      );
                    })
                  ) : (
                    <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                      {isAuthenticated ? "이번 달 AI 사용 기록이 없습니다." : "로그인하면 이번 달 AI 사용량이 표시됩니다."}
                    </div>
                  )}
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
                    <div className="text-4xl font-black text-amber-600">{billing?.creditBalance ?? 0}</div>
                    <div className="text-sm text-amber-700">사용 가능 크레딧</div>
                  </div>
                  <Button className="w-full bg-primary" onClick={() => setSearchParams({ tab: "credits" })}>
                    충전하러 가기
                  </Button>
                </CardContent>
              </Card>
            </div>
          </TabsContent>

          <TabsContent value="credits" className="mt-5">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {products.map((pack) => (
                <Card key={pack.code} className={`relative border-2 bg-white ${pack.badge ? "border-amber-400 shadow-lg" : "border-slate-200"}`}>
                  {pack.badge && <Badge className="absolute -top-3 left-1/2 -translate-x-1/2 bg-amber-500 text-white">{pack.badge}</Badge>}
                  <CardContent className="space-y-4 p-5 text-center">
                    <Zap className="mx-auto size-8 text-amber-500" />
                    <div>
                      <div className="text-4xl font-black text-slate-900">{pack.creditAmount}</div>
                      <div className="text-sm text-slate-500">크레딧</div>
                    </div>
                    <div className="text-xl font-black text-blue-600">{won(pack.price)}</div>
                    <Button
                      className="w-full"
                      disabled={!isAuthenticated || busy === `buy-${pack.code}`}
                      onClick={() => void doPurchase(pack.code)}
                    >
                      {busy === `buy-${pack.code}` && <Loader2 className="size-4 animate-spin" />}
                      {isAuthenticated ? "충전하기" : "로그인 필요"}
                    </Button>
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
                {payments.length > 0 ? (
                  payments.map((payment) => (
                    <div key={payment.id} className="flex flex-col gap-2 rounded-xl border border-slate-200 bg-slate-50 p-4 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <div className="text-sm font-semibold text-slate-800">
                          {payment.plan ? `${payment.plan} 플랜 구독` : payment.creditAmount ? `크레딧 ${payment.creditAmount}개 충전` : payment.productCode}
                        </div>
                        <div className="text-xs text-slate-500">{(payment.paidAt ?? payment.createdAt)?.slice(0, 10)} · {payment.provider}</div>
                      </div>
                      <div className="flex items-center gap-3">
                        <span className="text-sm font-black text-slate-900">{won(payment.amount)}</span>
                        <Badge className={payment.status === "PAID" ? "bg-green-100 text-green-700" : "bg-slate-200 text-slate-700"}>
                          {payment.status === "PAID" ? "결제 완료" : payment.status}
                        </Badge>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="rounded-lg bg-slate-50 p-6 text-center text-sm text-slate-500">
                    {isAuthenticated ? "결제 내역이 없습니다." : "로그인하면 결제 내역이 표시됩니다."}
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
