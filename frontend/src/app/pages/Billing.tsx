import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Award, BarChart3, CheckCircle2, CreditCard, ReceiptText, X, Zap } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../lib/api";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Progress } from "../components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { listCreditProducts } from "@/features/billing/api/creditProductsApi";
import { readyTossPayment } from "@/features/billing/api/paymentApi";
import { getMyBenefits, listSubscriptionPlans } from "@/features/billing/api/subscriptionApi";
import { requestTossCardPayment } from "@/features/billing/api/tossPaymentSdk";
import type { CreditProduct, MyBenefits, SubscriptionPlan } from "@/features/billing/types/billing";
import { subscriptionFallbackPlans, toDisplayPlans } from "@/features/billing/utils/subscriptionDisplay";

const tabs = ["plans", "usage", "credits", "history"] as const;
type BillingTab = (typeof tabs)[number];

const payments = [
  { date: "2026-06-01", item: "프로 플랜 월간 구독", amount: "29,000원", status: "결제 완료" },
  { date: "2026-05-20", item: "크레딧 30개", amount: "11,900원", status: "결제 완료" },
  { date: "2026-05-01", item: "베이직 플랜 월간 구독", amount: "9,900원", status: "결제 완료" },
];

const formatCurrency = (value: number) => `${value.toLocaleString("ko-KR")}원`;
const formatDate = (value?: string) => (value ? new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric" }).format(new Date(value)) : "");

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message;
  }
  return fallback;
}

export function BillingPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuth();
  const requestedTab = searchParams.get("tab") ?? "plans";
  const activeTab: BillingTab = tabs.includes(requestedTab as BillingTab) ? (requestedTab as BillingTab) : "plans";
  const [subscriptionPlans, setSubscriptionPlans] = useState<SubscriptionPlan[]>(() => subscriptionFallbackPlans());
  const [plansLoading, setPlansLoading] = useState(false);
  const [plansError, setPlansError] = useState<string | null>(null);
  const [myBenefits, setMyBenefits] = useState<MyBenefits | null>(null);
  const [benefitsLoading, setBenefitsLoading] = useState(false);
  const [benefitsError, setBenefitsError] = useState<string | null>(null);
  const [creditProducts, setCreditProducts] = useState<CreditProduct[]>([]);
  const [productsLoading, setProductsLoading] = useState(false);
  const [productsError, setProductsError] = useState<string | null>(null);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [payingCode, setPayingCode] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    setPlansLoading(true);
    setPlansError(null);
    listSubscriptionPlans()
      .then((plans) => {
        if (mounted) {
          setSubscriptionPlans(plans);
        }
      })
      .catch((error) => {
        if (mounted) {
          setPlansError(errorMessage(error, "구독 플랜 정책을 불러오지 못해 기본 정책으로 표시합니다."));
          setSubscriptionPlans(subscriptionFallbackPlans());
        }
      })
      .finally(() => {
        if (mounted) {
          setPlansLoading(false);
        }
      });

    setProductsLoading(true);
    setProductsError(null);
    listCreditProducts()
      .then((products) => {
        if (mounted) {
          setCreditProducts(products);
        }
      })
      .catch((error) => {
        if (mounted) {
          setProductsError(errorMessage(error, "크레딧 상품을 불러오지 못했습니다."));
        }
      })
      .finally(() => {
        if (mounted) {
          setProductsLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    if (!isAuthenticated) {
      setMyBenefits(null);
      setBenefitsError(null);
      setBenefitsLoading(false);
      return () => {
        mounted = false;
      };
    }

    setBenefitsLoading(true);
    setBenefitsError(null);
    getMyBenefits()
      .then((benefits) => {
        if (mounted) {
          setMyBenefits(benefits);
        }
      })
      .catch((error) => {
        if (mounted) {
          setBenefitsError(errorMessage(error, "사용권 잔여량을 불러오지 못했습니다."));
        }
      })
      .finally(() => {
        if (mounted) {
          setBenefitsLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, [isAuthenticated]);

  const balanceText = useMemo(() => (user?.credit ?? 0).toLocaleString("ko-KR"), [user?.credit]);
  const displayPlans = useMemo(() => toDisplayPlans(subscriptionPlans), [subscriptionPlans]);
  const benefitUsageRows = useMemo(
    () =>
      (myBenefits?.benefits ?? []).map((benefit) => ({
        feature: benefit.benefitName,
        used: benefit.usedQuantity,
        limit: benefit.grantedQuantity,
        remaining: benefit.remainingQuantity,
      })),
    [myBenefits],
  );

  async function handleCreditPurchase(product: CreditProduct) {
    if (!isAuthenticated) {
      navigate("/login");
      return;
    }

    setPaymentError(null);
    setPayingCode(product.code);
    try {
      const ready = await readyTossPayment(product.code);
      await requestTossCardPayment(ready);
    } catch (error) {
      setPaymentError(errorMessage(error, "결제창을 열지 못했습니다."));
    } finally {
      setPayingCode(null);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
            <CreditCard className="size-6 text-blue-600" />
            결제/구독
          </h1>
          <p className="mt-1 text-sm text-slate-500">요금제, AI 사용량, 크레딧 충전, 결제 내역을 한 곳에서 관리합니다.</p>
        </div>

        <Tabs value={activeTab} onValueChange={(value) => setSearchParams({ tab: value })}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-white p-1">
            <TabsTrigger value="plans">요금제</TabsTrigger>
            <TabsTrigger value="usage">AI 사용량</TabsTrigger>
            <TabsTrigger value="credits">크레딧 충전</TabsTrigger>
            <TabsTrigger value="history">결제 내역</TabsTrigger>
          </TabsList>

          <TabsContent value="plans" className="mt-5">
            {plansError && (
              <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-semibold text-amber-800">
                {plansError}
              </div>
            )}
            {plansLoading && (
              <Card className="mb-4 border border-slate-200 bg-white">
                <CardContent className="p-5 text-center text-sm text-slate-500">구독 플랜 정책을 불러오는 중입니다.</CardContent>
              </Card>
            )}
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {displayPlans.map((plan) => (
                <Card key={plan.code} className={`relative border-2 bg-white ${plan.highlighted ? "border-blue-500 shadow-lg" : "border-slate-200"}`}>
                  {plan.badge && <Badge className="absolute -top-3 left-1/2 -translate-x-1/2 bg-blue-600 text-white">{plan.badge}</Badge>}
                  <CardHeader>
                    <CardTitle className="text-lg">{plan.name} 플랜</CardTitle>
                    <p className="text-sm text-slate-500">{plan.description}</p>
                    <div className="pt-2 text-3xl font-black text-slate-900">{plan.monthlyPrice}</div>
                    <div className="text-xs font-semibold text-slate-400">/{plan.period}</div>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="space-y-2">
                      {plan.benefits.map((benefit) => (
                        <div key={benefit.code} className="flex items-center gap-2 text-sm">
                          {benefit.disabled ? (
                            <X className="size-4 shrink-0 text-slate-300" />
                          ) : benefit.premium ? (
                            <Zap className="size-4 shrink-0 text-purple-500" />
                          ) : (
                            <CheckCircle2 className="size-4 shrink-0 text-green-600" />
                          )}
                          <span className={benefit.disabled ? "text-slate-400" : benefit.premium ? "font-semibold text-purple-700" : "text-slate-700"}>
                            {benefit.label} <span className={benefit.disabled ? "text-slate-400" : "font-bold text-slate-900"}>{benefit.text}</span>
                          </span>
                        </div>
                      ))}
                    </div>
                    <Button className={plan.highlighted ? "w-full bg-gradient-to-r from-blue-600 to-indigo-600" : "w-full"} variant={plan.highlighted ? "default" : "outline"}>
                      플랜 선택
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
                    이번 달 사용권
                  </CardTitle>
                  {myBenefits && (
                    <p className="text-xs text-slate-500">
                      {formatDate(myBenefits.periodStart)}부터 {formatDate(myBenefits.periodEnd)} 전까지 적용되는 {myBenefits.planCode} 플랜 사용권입니다.
                    </p>
                  )}
                </CardHeader>
                <CardContent className="space-y-4">
                  {!isAuthenticated ? (
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-5 text-center text-sm text-slate-500">
                      로그인하면 현재 플랜의 사용권 잔여량을 확인할 수 있습니다.
                    </div>
                  ) : benefitsLoading ? (
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-5 text-center text-sm text-slate-500">사용권 잔여량을 불러오는 중입니다.</div>
                  ) : benefitsError ? (
                    <div className="rounded-lg border border-red-200 bg-red-50 p-5 text-center text-sm font-semibold text-red-700">{benefitsError}</div>
                  ) : benefitUsageRows.length === 0 ? (
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-5 text-center text-sm text-slate-500">이번 달 발급된 사용권이 없습니다.</div>
                  ) : (
                    benefitUsageRows.map((row) => {
                      const pct = row.limit <= 0 ? 0 : Math.round((row.used / row.limit) * 100);
                      return (
                        <div key={row.feature} className="space-y-1.5">
                          <div className="flex items-center justify-between text-sm">
                            <span className="font-semibold text-slate-700">{row.feature}</span>
                            <span className="text-xs text-slate-500">
                              {row.used}/{row.limit}장 사용 · {row.remaining}장 남음
                            </span>
                          </div>
                          <Progress value={pct} className="h-2" />
                        </div>
                      );
                    })
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
                    <div className="text-4xl font-black text-amber-600">{balanceText}</div>
                    <div className="text-sm text-amber-700">사용 가능 크레딧</div>
                  </div>
                  <Button className="w-full bg-gradient-to-r from-amber-500 to-orange-500" onClick={() => setSearchParams({ tab: "credits" })}>
                    충전하러 가기
                  </Button>
                </CardContent>
              </Card>
            </div>
          </TabsContent>

          <TabsContent value="credits" className="mt-5">
            <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
              현재 충전 가능한 상품은 DB에 등록된 크레딧 4종입니다. 결제 금액과 지급 크레딧은 서버에서 다시 확정됩니다.
            </div>
            {paymentError && (
              <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                {paymentError}
              </div>
            )}
            {productsError && (
              <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                {productsError}
              </div>
            )}
            {productsLoading ? (
              <Card className="border border-slate-200 bg-white">
                <CardContent className="p-8 text-center text-sm text-slate-500">크레딧 상품을 불러오는 중입니다.</CardContent>
              </Card>
            ) : (
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                {creditProducts.map((product) => (
                  <Card key={product.code} className={`relative border-2 bg-white ${product.badge ? "border-amber-400 shadow-lg" : "border-slate-200"}`}>
                    {product.badge && <Badge className="absolute -top-3 left-1/2 -translate-x-1/2 bg-amber-500 text-white">{product.badge}</Badge>}
                    <CardContent className="space-y-4 p-5 text-center">
                      <Zap className="mx-auto size-8 text-amber-500" />
                      <div>
                        <div className="text-4xl font-black text-slate-900">{product.creditAmount}</div>
                        <div className="text-sm text-slate-500">크레딧</div>
                      </div>
                      <div className="text-xl font-black text-blue-600">{formatCurrency(product.price)}</div>
                      {product.description && <div className="min-h-5 text-xs text-slate-500">{product.description}</div>}
                      <Button className="w-full" disabled={payingCode !== null} onClick={() => handleCreditPurchase(product)}>
                        {payingCode === product.code ? "결제 준비 중" : "구매하기"}
                      </Button>
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
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
                      <Badge className="bg-green-100 text-green-700">{payment.status}</Badge>
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
