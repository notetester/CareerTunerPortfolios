import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Progress } from "../components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { Award, BarChart3, CheckCircle2, CreditCard, Loader2, ReceiptText, RotateCcw, Zap } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { subscribeCreditBalanceChanged } from "../lib/creditBalanceEvents";
import {
  cancelSubscription, getCreditProducts, getMonthlyUsage, getMyBilling, getMyPayments, getPlans,
  subscribe,
  type CreditProduct, type MyBilling, type Payment, type SubscriptionPlan, type UsageRow,
} from "@/features/billing/api/billingApi";
import { cancelTossPayment, readyTossPayment } from "@/features/billing/api/paymentApi";
import { requestTossCardPayment } from "@/features/billing/api/tossPaymentSdk";
import type { TossPaymentReadyResponse } from "@/features/billing/types/billing";
import {
  acknowledgeRefundPolicy,
  createPolicyActionKey,
  getCurrentRefundPolicy,
  type CurrentRefundPolicy,
} from "@/features/billing/api/refundPolicyApi";
import { RefundPolicyConfirmDialog } from "@/features/billing/components/RefundPolicyConfirmDialog";
import { RefundRequestDialog } from "@/features/billing/components/RefundRequestDialog";
import { getAiFeatureLabel } from "@/features/billing/aiFeatureLabels";
import {
  createRefundRequest, getMyRefundRequests, previewRefundRequest,
  type RefundEligibility, type RefundReasonCode, type RefundRequestRow,
} from "@/features/billing/api/refundRequestApi";

const tabs = ["plans", "usage", "credits", "history"] as const;
type BillingTab = (typeof tabs)[number];
type PendingPayment = {
  productCode: string;
  productType: "CREDIT" | "SUBSCRIPTION";
  policyAcknowledgementKey: string;
};
const billingTabTriggerClass =
  "min-w-32 rounded-lg border border-transparent transition-all duration-150 hover:bg-slate-100 hover:text-slate-950 hover:shadow-sm active:scale-[0.98] data-[state=active]:border-blue-600 data-[state=active]:bg-blue-600 data-[state=active]:text-white data-[state=active]:shadow-sm";

// 요금제 기능 설명(마케팅 카피)은 코드별로 클라이언트에 둔다.
const PLAN_FEATURES: Record<string, string[]> = {
  FREE: ["공고 분석 월 3회", "텍스트 면접 월 1회", "지원 건 3건 저장"],
  BASIC: ["공고 분석 월 20회", "텍스트 면접 무제한", "답변 첨삭 월 5회"],
  PRO: ["공고 분석 무제한", "음성 면접", "장기 취업 분석"],
  PREMIUM: ["아바타 면접관", "영상/자세 분석", "1:1 전략 컨설팅"],
};

const won = (n: number) => `${n.toLocaleString("ko-KR")}원`;

function benefitLabel(plan: SubscriptionPlan, fallback: string[]) {
  const benefits = (plan.benefits ?? []).filter((benefit) => benefit.active !== false);
  if (benefits.length === 0) return fallback;
  return benefits.map((benefit) => {
    const quantity = benefit.quantity <= 0 ? "미제공" : `${benefit.quantity.toLocaleString("ko-KR")}회`;
    const overage = benefit.overagePolicy === "CREDIT" || benefit.overagePolicy === "FALLBACK_CREDIT"
      ? ` · 초과 ${benefit.creditCost.toLocaleString("ko-KR")}크레딧`
      : benefit.overagePolicy === "BLOCK"
        ? " · 초과 사용 불가"
        : "";
    return `${benefit.benefitName} ${quantity}${overage}`;
  });
}

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
  const [refundRequests, setRefundRequests] = useState<RefundRequestRow[]>([]);
  const [refundPayment, setRefundPayment] = useState<Payment | null>(null);
  const [refundBusy, setRefundBusy] = useState(false);
  const [refundEligibility, setRefundEligibility] = useState<RefundEligibility | null>(null);
  const [refundError, setRefundError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refundPolicy, setRefundPolicy] = useState<CurrentRefundPolicy | null>(null);
  const [pendingPayment, setPendingPayment] = useState<PendingPayment | null>(null);
  const [readyPayment, setReadyPayment] = useState<TossPaymentReadyResponse | null>(null);
  const [policyBusy, setPolicyBusy] = useState(false);
  const [paymentLaunchBusy, setPaymentLaunchBusy] = useState(false);
  const pendingPlanCode = busy?.startsWith("sub-") ? busy.slice(4) : null;
  const pendingProductCode = busy?.startsWith("buy-") ? busy.slice(4) : null;
  const subscriptionPeriodEnd = billing?.periodEnd ?? null;
  const isCancelScheduled = billing?.subscriptionStatus === "CANCELED" && subscriptionPeriodEnd !== null;
  const isSubscriptionActive = billing?.subscriptionStatus === "ACTIVE" && subscriptionPeriodEnd !== null;

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
    try {
      setRefundRequests(await getMyRefundRequests());
    } catch {
      setRefundRequests([]);
    }
  };

  useEffect(() => {
    void loadPublic().catch(() => setError("요금제 정보를 불러오지 못했습니다."));
    void loadMine().catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);

  useEffect(() => subscribeCreditBalanceChanged(() => {
    void loadMine().catch(() => {});
  }), [isAuthenticated]);

  const doSubscribe = async (planCode: string) => {
    setBusy(`sub-${planCode}`);
    setError(null);
    try {
      if (planCode === "FREE") {
        setBilling(await subscribe(planCode, "MONTHLY"));
        await loadMine();
      } else {
        const policy = await getCurrentRefundPolicy();
        setRefundPolicy(policy);
        setPendingPayment({
          productCode: planCode,
          productType: "SUBSCRIPTION",
          policyAcknowledgementKey: createPolicyActionKey("PAYMENT"),
        });
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "구독 결제 준비에 실패했습니다.");
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
      const policy = await getCurrentRefundPolicy();
      setRefundPolicy(policy);
      setPendingPayment({
        productCode,
        productType: "CREDIT",
        policyAcknowledgementKey: createPolicyActionKey("PAYMENT"),
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "크레딧 결제 준비에 실패했습니다.");
    } finally {
      setBusy(null);
    }
  };

  const confirmPolicyAndPay = async () => {
    if (!pendingPayment || !refundPolicy) {
      setError("결제에 적용할 환불 정책을 다시 확인해 주세요.");
      return;
    }
    setPolicyBusy(true);
    setError(null);
    try {
      await acknowledgeRefundPolicy(
        refundPolicy.id,
        "PAYMENT",
        pendingPayment.policyAcknowledgementKey,
      );
      const ready = await readyTossPayment(
        pendingPayment.productCode,
        pendingPayment.productType,
        refundPolicy.id,
        pendingPayment.policyAcknowledgementKey,
      );
      setReadyPayment(ready);
      setPendingPayment(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "결제 준비에 실패했습니다.");
      setPendingPayment(null);
    } finally {
      setPolicyBusy(false);
    }
  };

  const openTossPayment = async () => {
    if (!readyPayment) return;
    const ready = readyPayment;
    setPaymentLaunchBusy(true);
    setError(null);
    try {
      await requestTossCardPayment(ready);
    } catch (e) {
      try {
        await cancelTossPayment(ready.orderId);
      } catch {
        // 결제창 오류를 우선 노출하고, 취소 API 실패는 다음 결제 내역 갱신에서 정리한다.
      }
      setReadyPayment(null);
      setError(e instanceof Error ? e.message : "Toss 결제창을 열지 못했습니다.");
      await loadMine().catch(() => {});
    } finally {
      setPaymentLaunchBusy(false);
    }
  };

  const cancelPreparedPayment = async () => {
    if (!readyPayment || paymentLaunchBusy) return;
    const orderId = readyPayment.orderId;
    setPaymentLaunchBusy(true);
    setError(null);
    try {
      await cancelTossPayment(orderId);
      setReadyPayment(null);
      await loadMine();
    } catch (e) {
      setError(e instanceof Error ? e.message : "준비된 결제를 취소하지 못했습니다.");
    } finally {
      setPaymentLaunchBusy(false);
    }
  };

  const openRefundRequest = async (payment: Payment) => {
    setRefundPayment(payment);
    setRefundEligibility(null);
    setRefundError(null);
    setRefundBusy(true);
    try {
      const [policy, eligibility] = await Promise.all([
        getCurrentRefundPolicy(),
        previewRefundRequest(payment.id, "CHANGE_OF_MIND"),
      ]);
      setRefundPolicy(policy);
      setRefundEligibility(eligibility);
    } catch (e) {
      setRefundError(e instanceof Error ? e.message : "환불 가능 여부를 확인하지 못했습니다.");
    } finally {
      setRefundBusy(false);
    }
  };

  const previewRefundReason = async (reasonCode: RefundReasonCode) => {
    if (!refundPayment) return;
    setRefundBusy(true);
    setRefundError(null);
    try {
      setRefundEligibility(await previewRefundRequest(refundPayment.id, reasonCode));
    } catch (e) {
      setRefundEligibility(null);
      setRefundError(e instanceof Error ? e.message : "환불 가능 여부를 확인하지 못했습니다.");
    } finally {
      setRefundBusy(false);
    }
  };

  const submitRefundRequest = async (reasonCode: RefundReasonCode, reasonText: string) => {
    if (!refundPayment) return;
    setRefundBusy(true);
    setRefundError(null);
    try {
      await createRefundRequest(refundPayment.id, reasonCode, reasonText);
      setRefundPayment(null);
      setRefundEligibility(null);
      await loadMine();
    } catch (e) {
      setRefundError(e instanceof Error ? e.message : "환불 신청에 실패했습니다.");
    } finally {
      setRefundBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <RefundPolicyConfirmDialog
        open={pendingPayment !== null}
        policy={refundPolicy}
        busy={policyBusy}
        onCancel={() => setPendingPayment(null)}
        onConfirm={() => void confirmPolicyAndPay()}
      />
      <RefundRequestDialog
        payment={refundPayment}
        policy={refundPolicy}
        eligibility={refundEligibility}
        error={refundError}
        busy={refundBusy}
        onCancel={() => {
          setRefundPayment(null);
          setRefundEligibility(null);
          setRefundError(null);
        }}
        onReasonChange={(reasonCode) => void previewRefundReason(reasonCode)}
        onSubmit={(reasonCode, reasonText) => void submitRefundRequest(reasonCode, reasonText)}
      />
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
            <CreditCard className="size-6 text-blue-600" />
            결제/구독
          </h1>
          <p className="mt-1 text-sm text-slate-500">요금제, AI 사용량, 크레딧 충전, 결제 내역을 한 곳에서 관리합니다</p>
        </div>

        {billing && (
          <div
            className={`flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4 ${
              isCancelScheduled ? "border-amber-200 bg-amber-50" : "border-blue-200 bg-blue-50"
            }`}
          >
            <div className="text-sm text-blue-900">
              현재 플랜 <strong>{billing.currentPlanName}</strong>
              {isSubscriptionActive && (
                <span className="ml-2 text-xs text-blue-700">~ {subscriptionPeriodEnd?.slice(0, 10)}</span>
              )}
              {isCancelScheduled && (
                <span className="ml-2 text-xs font-semibold text-amber-700">
                  해지 예약됨 · {subscriptionPeriodEnd?.slice(0, 10)}까지 이용 가능
                </span>
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
        {readyPayment && (
          <div className="flex flex-col gap-4 rounded-xl border border-blue-200 bg-blue-50 p-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="font-semibold text-blue-950">Toss 결제 준비가 완료되었습니다.</div>
              <div className="mt-1 text-sm text-blue-800">
                {readyPayment.orderName} · {won(readyPayment.amount)}
              </div>
              <div className="mt-1 text-xs text-blue-700">정책 확인 창을 닫았습니다. 아래 버튼을 눌러 안전하게 결제창을 여세요.</div>
            </div>
            <div className="flex shrink-0 gap-2">
              <Button variant="outline" disabled={paymentLaunchBusy} onClick={() => void cancelPreparedPayment()}>
                결제 취소
              </Button>
              <Button disabled={paymentLaunchBusy} onClick={() => void openTossPayment()}>
                {paymentLaunchBusy && <Loader2 className="size-4 animate-spin" />}
                Toss 결제창 열기
              </Button>
            </div>
          </div>
        )}

        <Tabs value={activeTab} onValueChange={(value) => setSearchParams({ tab: value })}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-card p-1">
            <TabsTrigger value="plans" className={billingTabTriggerClass}>요금제</TabsTrigger>
            <TabsTrigger value="usage" className={billingTabTriggerClass}>AI 사용량</TabsTrigger>
            <TabsTrigger value="credits" className={billingTabTriggerClass}>크레딧 충전</TabsTrigger>
            <TabsTrigger value="history" className={billingTabTriggerClass}>결제 내역</TabsTrigger>
          </TabsList>

          <TabsContent value="plans" className="mt-5">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {plans.map((plan) => {
                const isCurrent = billing?.currentPlanCode === plan.code;
                const popular = plan.code === "PRO";
                const isPending = pendingPlanCode === plan.code;
                return (
                  <Card
                    key={plan.code}
                    className={`relative border-2 transition-all duration-200 ${
                      isPending
                        ? "border-blue-600 bg-blue-50/70 shadow-xl shadow-blue-100 ring-2 ring-blue-200"
                        : popular
                          ? "border-blue-500 bg-card shadow-lg"
                          : "border-slate-200 bg-card hover:border-slate-300 hover:shadow-md"
                    }`}
                    aria-busy={isPending}
                  >
                    {popular && <Badge className="absolute -top-3 left-1/2 -translate-x-1/2 bg-blue-600 text-white">추천</Badge>}
                    <CardHeader>
                      <CardTitle className="text-lg">{plan.name} 플랜</CardTitle>
                      <p className="text-sm text-slate-500">{plan.description}</p>
                      <div className="pt-2 text-3xl font-black text-slate-900">{won(plan.monthlyPrice)}</div>
                      {(plan.yearlyPrice ?? 0) > 0 && <div className="text-xs text-slate-400">연간 {won(plan.yearlyPrice ?? 0)}/월</div>}
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <div className="space-y-2">
                        {benefitLabel(plan, PLAN_FEATURES[plan.code] ?? []).map((feature) => (
                          <div key={feature} className="flex items-center gap-2 text-sm text-slate-700">
                            <CheckCircle2 className="size-4 text-green-600" />
                            {feature}
                          </div>
                        ))}
                      </div>
                      <Button
                        disabled={!isAuthenticated || isCurrent || busy !== null || readyPayment !== null}
                        className={isPending ? "w-full bg-blue-700 shadow-lg shadow-blue-200" : popular ? "w-full bg-primary" : "w-full"}
                        variant={popular ? "default" : "outline"}
                        onClick={() => void doSubscribe(plan.code)}
                      >
                        {isPending && <Loader2 className="size-4 animate-spin" />}
                        {isPending ? "결제창 준비 중" : isCurrent ? "현재 플랜" : plan.code === "FREE" ? "무료 사용" : "이 플랜 구독"}
                      </Button>
                      {isPending && (
                        <div className="rounded-md border border-blue-200 bg-white/80 px-3 py-2 text-center text-xs font-semibold text-blue-700">
                          선택한 요금제 결제 요청을 보내고 있습니다.
                        </div>
                      )}
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          </TabsContent>

          <TabsContent value="usage" className="mt-5">
            <div className="grid gap-4 lg:grid-cols-3">
              <Card className="border border-slate-200 bg-card lg:col-span-2">
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
                            <span className="font-semibold text-slate-700">{getAiFeatureLabel(row.featureType)}</span>
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
              <Card className="border border-slate-200 bg-card">
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
              {products.map((pack) => {
                const isPending = pendingProductCode === pack.code;
                return (
                  <Card
                    key={pack.code}
                    className={`relative border-2 transition-all duration-200 ${
                      isPending
                        ? "border-amber-500 bg-amber-50/80 shadow-xl shadow-amber-100 ring-2 ring-amber-200"
                        : pack.badge
                          ? "border-amber-400 bg-card shadow-lg"
                          : "border-slate-200 bg-card hover:border-slate-300 hover:shadow-md"
                    }`}
                    aria-busy={isPending}
                  >
                    {pack.badge && <Badge className="absolute -top-3 left-1/2 -translate-x-1/2 bg-amber-500 text-white">{pack.badge}</Badge>}
                    <CardContent className="space-y-4 p-5 text-center">
                      <Zap className={`mx-auto size-8 ${isPending ? "text-amber-600" : "text-amber-500"}`} />
                      <div>
                        <div className="text-4xl font-black text-slate-900">{pack.creditAmount}</div>
                        <div className="text-sm text-slate-500">크레딧</div>
                      </div>
                      <div className="text-xl font-black text-blue-600">{won(pack.price)}</div>
                      <Button
                        className={isPending ? "w-full bg-amber-600 text-white hover:bg-amber-600" : "w-full"}
                        disabled={!isAuthenticated || busy !== null || readyPayment !== null}
                        onClick={() => void doPurchase(pack.code)}
                      >
                        {isPending && <Loader2 className="size-4 animate-spin" />}
                        {isPending ? "결제창 준비 중" : isAuthenticated ? "충전하기" : "로그인 필요"}
                      </Button>
                      {isPending && (
                        <div className="rounded-md border border-amber-200 bg-white/80 px-3 py-2 text-xs font-semibold text-amber-700">
                          선택한 크레딧 상품 결제 요청을 보내고 있습니다.
                        </div>
                      )}
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          </TabsContent>

          <TabsContent value="history" className="mt-5">
            <div className="space-y-4">
            <Card className="border border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <ReceiptText className="size-4 text-slate-600" />
                  결제 내역
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {payments.length > 0 ? (
                  payments.map((payment) => {
                    const refund = refundRequests.find((row) => row.paymentId === payment.id);
                    return (
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
                          {payment.status === "PAID" ? "결제 완료" : "결제 취소"}
                        </Badge>
                        {payment.status === "PAID" && !refund && (
                          <Button size="sm" variant="outline" onClick={() => void openRefundRequest(payment)}>
                            환불 신청
                          </Button>
                        )}
                        {refund && <RefundStatusBadge status={refund.status} />}
                      </div>
                    </div>
                    );
                  })
                ) : (
                  <div className="rounded-lg bg-slate-50 p-6 text-center text-sm text-slate-500">
                    {isAuthenticated ? "결제 내역이 없습니다." : "로그인하면 결제 내역이 표시됩니다."}
                  </div>
                )}
              </CardContent>
            </Card>
            <Card className="border border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <RotateCcw className="size-4 text-slate-600" />
                  환불 신청 및 처리 내역
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {refundRequests.length > 0 ? refundRequests.map((refund) => (
                  <div key={refund.id} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <div className="text-sm font-semibold text-slate-800">주문번호 {refund.orderId}</div>
                        <div className="mt-1 text-xs text-slate-500">
                          {refund.requestedAt.slice(0, 10)} 신청 · {won(refund.refundAmount)} · {refund.creditUsed || refund.benefitUsed ? "사용 이력 있음" : "사용 이력 없음"}
                        </div>
                      </div>
                      <RefundStatusBadge status={refund.status} />
                    </div>
                    {refund.reviewedReason && <p className="mt-2 text-xs text-slate-600">관리자 답변: {refund.reviewedReason}</p>}
                  </div>
                )) : (
                  <div className="rounded-lg bg-slate-50 p-6 text-center text-sm text-slate-500">환불 신청 내역이 없습니다.</div>
                )}
              </CardContent>
            </Card>
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}

function RefundStatusBadge({ status }: { status: RefundRequestRow["status"] }) {
  const label = status === "REQUESTED" ? "검토 중" : status === "APPROVED" ? "전액 환불" : "환불 불가";
  const color = status === "REQUESTED" ? "bg-amber-100 text-amber-700" : status === "APPROVED" ? "bg-blue-100 text-blue-700" : "bg-slate-200 text-slate-700";
  return <Badge className={color}>{label}</Badge>;
}
