import { useState } from "react";
import { useNavigate } from "react-router";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { CheckCircle2, X, Award, CreditCard, Zap } from "lucide-react";

const plans = [
  {
    name: "무료",
    price: { monthly: "0원", yearly: "0원" },
    period: "영구 무료",
    badge: null,
    highlighted: false,
    features: [
      { label: "공고 분석", value: "월 3회" },
      { label: "기본 예상 질문 생성", value: true },
      { label: "텍스트 모의면접", value: "월 1회" },
      { label: "기본 분석 리포트", value: true },
      { label: "지원 건 저장", value: "3건" },
      { label: "커뮤니티 이용", value: true },
      { label: "고급 답변 첨삭", value: false },
      { label: "음성 AI 면접", value: false },
      { label: "기업 현황 조사", value: false },
      { label: "장기 취업 경향 분석", value: false },
    ],
    cta: "무료 시작",
    ctaVariant: "outline" as const,
  },
  {
    name: "베이직",
    price: { monthly: "9,900원", yearly: "7,900원" },
    period: "월",
    badge: null,
    highlighted: false,
    features: [
      { label: "공고 분석", value: "월 20회" },
      { label: "기본 예상 질문 생성", value: true },
      { label: "텍스트 모의면접", value: "무제한" },
      { label: "기본 분석 리포트", value: true },
      { label: "지원 건 저장", value: "20건" },
      { label: "커뮤니티 이용", value: true },
      { label: "고급 답변 첨삭", value: "월 5회" },
      { label: "음성 AI 면접", value: false },
      { label: "기업 현황 조사", value: false },
      { label: "장기 취업 경향 분석", value: false },
    ],
    cta: "시작하기",
    ctaVariant: "outline" as const,
  },
  {
    name: "프로",
    price: { monthly: "29,000원", yearly: "23,000원" },
    period: "월",
    badge: "인기",
    highlighted: true,
    features: [
      { label: "공고 분석", value: "무제한" },
      { label: "기본 예상 질문 생성", value: true },
      { label: "텍스트 모의면접", value: "무제한" },
      { label: "기본 분석 리포트", value: true },
      { label: "지원 건 저장", value: "무제한" },
      { label: "커뮤니티 이용", value: true },
      { label: "고급 답변 첨삭", value: "무제한" },
      { label: "음성 AI 면접", value: true },
      { label: "기업 현황 조사", value: true },
      { label: "장기 취업 경향 분석", value: true },
    ],
    cta: "시작하기",
    ctaVariant: "default" as const,
  },
  {
    name: "프리미엄",
    price: { monthly: "49,000원", yearly: "39,000원" },
    period: "월",
    badge: null,
    highlighted: false,
    features: [
      { label: "공고 분석", value: "무제한" },
      { label: "기본 예상 질문 생성", value: true },
      { label: "텍스트 모의면접", value: "무제한" },
      { label: "기본 분석 리포트", value: true },
      { label: "지원 건 저장", value: "무제한" },
      { label: "커뮤니티 이용", value: true },
      { label: "고급 답변 첨삭", value: "무제한" },
      { label: "음성 AI 면접", value: true },
      { label: "기업 현황 조사", value: true },
      { label: "장기 취업 경향 분석", value: true },
    ],
    extraFeatures: ["영상 표정/자세 분석", "자기소개서 고급 첨삭", "포트폴리오 첨삭", "AI 아바타 면접관", "1:1 전략 컨설팅"],
    cta: "시작하기",
    ctaVariant: "outline" as const,
  },
];

const creditOptions = [
  { amount: 10, price: "4,900원", perCredit: "490원/개" },
  { amount: 30, price: "11,900원", perCredit: "396원/개", popular: true },
  { amount: 50, price: "18,000원", perCredit: "360원/개" },
  { amount: 100, price: "29,000원", perCredit: "290원/개", best: true },
];

const creditFeatures = [
  { feature: "공고문 분석", credit: 1, icon: "📄" },
  { feature: "기업 현황 조사", credit: 2, icon: "🏢" },
  { feature: "예상 질문 생성", credit: 1, icon: "❓" },
  { feature: "텍스트 모의면접", credit: 2, icon: "💬" },
  { feature: "음성 모의면접", credit: 3, icon: "🎤" },
  { feature: "영상/자세 분석 면접", credit: 5, icon: "📹" },
  { feature: "자기소개서 첨삭", credit: 2, icon: "✍️" },
  { feature: "전체 전략 리포트", credit: 3, icon: "📊" },
];

export function PricingPage() {
  const [billingCycle, setBillingCycle] = useState<"monthly" | "yearly">("monthly");
  const [activeTab, setActiveTab] = useState<"subscription" | "credits">("subscription");
  const navigate = useNavigate();

  return (
    <div className="bg-slate-50 min-h-screen">
      <div className="max-w-[1400px] mx-auto px-6 py-12 space-y-12">
        {/* Header */}
        <div className="text-center space-y-4">
          <Badge className="bg-green-100 text-green-700 px-4 py-1.5">요금제</Badge>
          <h1 className="text-4xl font-black text-slate-900">나에게 맞는 플랜을 선택하세요</h1>
          <p className="text-lg text-slate-500">무료 플랜으로 시작하고, 필요할 때 업그레이드하세요. 언제든지 취소 가능합니다.</p>
        </div>

        {/* Tab toggle */}
        <div className="flex justify-center">
          <div className="bg-white border border-slate-200 rounded-xl p-1 flex">
            <button
              onClick={() => setActiveTab("subscription")}
              className={`px-6 py-2 rounded-lg text-sm font-semibold transition-colors ${activeTab === "subscription" ? "bg-blue-600 text-white" : "text-slate-600 hover:text-blue-600"}`}
            >
              월 구독형
            </button>
            <button
              onClick={() => setActiveTab("credits")}
              className={`px-6 py-2 rounded-lg text-sm font-semibold transition-colors ${activeTab === "credits" ? "bg-blue-600 text-white" : "text-slate-600 hover:text-blue-600"}`}
            >
              크레딧형
            </button>
          </div>
        </div>

        {activeTab === "subscription" && (
          <>
            {/* Billing toggle */}
            <div className="flex justify-center items-center gap-3">
              <span className={`text-sm font-semibold ${billingCycle === "monthly" ? "text-slate-900" : "text-slate-400"}`}>월간 결제</span>
              <button
                onClick={() => setBillingCycle(billingCycle === "monthly" ? "yearly" : "monthly")}
                className={`relative w-12 h-6 rounded-full transition-colors ${billingCycle === "yearly" ? "bg-blue-600" : "bg-slate-200"}`}
              >
                <div className={`absolute top-0.5 size-5 rounded-full bg-white shadow-sm transition-transform ${billingCycle === "yearly" ? "translate-x-6" : "translate-x-0.5"}`} />
              </button>
              <div className="flex items-center gap-2">
                <span className={`text-sm font-semibold ${billingCycle === "yearly" ? "text-slate-900" : "text-slate-400"}`}>연간 결제</span>
                <Badge className="bg-green-100 text-green-700 text-xs px-2 py-0.5">2개월 무료</Badge>
              </div>
            </div>

            {/* Plans grid */}
            <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-5">
              {plans.map((plan) => (
                <Card key={plan.name} className={`relative border-2 ${plan.highlighted ? "border-blue-500 shadow-2xl" : "border-slate-200"}`}>
                  {plan.badge && (
                    <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                      <Badge className="bg-gradient-to-r from-blue-600 to-indigo-600 text-white px-4 py-0.5">{plan.badge}</Badge>
                    </div>
                  )}
                  <CardHeader className="text-center pt-8 pb-4">
                    <CardTitle className="text-xl">{plan.name} 플랜</CardTitle>
                    <div className="mt-2">
                      <div className="text-3xl font-black">
                        {billingCycle === "monthly" ? plan.price.monthly : plan.price.yearly}
                      </div>
                      <div className="text-slate-500 text-sm">/{plan.period}</div>
                      {billingCycle === "yearly" && plan.price.monthly !== "0원" && (
                        <div className="text-xs text-green-600 font-semibold mt-1">연 {plan.price.monthly} → {plan.price.yearly}</div>
                      )}
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-5">
                    <div className="space-y-2">
                      {plan.features.map((f) => (
                        <div key={f.label} className="flex items-center gap-2 text-sm">
                          {f.value === false ? (
                            <X className="size-4 text-slate-300 flex-shrink-0" />
                          ) : (
                            <CheckCircle2 className="size-4 text-green-600 flex-shrink-0" />
                          )}
                          <span className={f.value === false ? "text-slate-400" : "text-slate-700"}>
                            {f.label}
                            {typeof f.value === "string" && f.value !== "true" && (
                              <span className="font-semibold text-slate-900 ml-1">({f.value})</span>
                            )}
                          </span>
                        </div>
                      ))}
                      {"extraFeatures" in plan && plan.extraFeatures && plan.extraFeatures.map((f) => (
                        <div key={f} className="flex items-center gap-2 text-sm">
                          <Zap className="size-4 text-purple-500 flex-shrink-0" />
                          <span className="text-purple-700 font-medium">{f}</span>
                        </div>
                      ))}
                    </div>
                    <Button
                      className={`w-full ${plan.highlighted ? "bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700" : ""}`}
                      variant={plan.ctaVariant}
                      onClick={() => navigate("/login")}
                    >
                      {plan.cta}
                    </Button>
                  </CardContent>
                </Card>
              ))}
            </div>
          </>
        )}

        {activeTab === "credits" && (
          <div className="max-w-4xl mx-auto space-y-8">
            <div className="text-center">
              <p className="text-slate-600">AI 기능을 필요한 만큼만 사용하는 크레딧 방식입니다.<br />가입 시 무료 크레딧 <strong>10개</strong>를 제공합니다.</p>
            </div>

            {/* Credit cost table */}
            <Card className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <Award className="size-4 text-amber-600" />
                  기능별 크레딧 소모량
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid md:grid-cols-2 gap-3">
                  {creditFeatures.map((item) => (
                    <div key={item.feature} className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200">
                      <div className="flex items-center gap-2">
                        <span className="text-lg">{item.icon}</span>
                        <span className="text-sm font-medium text-slate-700">{item.feature}</span>
                      </div>
                      <div className="flex items-center gap-1 text-sm font-black text-amber-600">
                        <Award className="size-3.5" />
                        {item.credit}
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>

            {/* Credit packages */}
            <div>
              <h3 className="font-bold text-slate-800 mb-4 text-lg">크레딧 패키지</h3>
              <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-4">
                {creditOptions.map((opt) => (
                  <Card key={opt.amount} className={`border-2 relative ${opt.popular ? "border-blue-500 shadow-xl" : opt.best ? "border-green-500" : "border-slate-200"}`}>
                    {opt.popular && (
                      <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                        <Badge className="bg-blue-600 text-white px-3 py-0.5">인기</Badge>
                      </div>
                    )}
                    {opt.best && (
                      <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                        <Badge className="bg-green-600 text-white px-3 py-0.5">최저가</Badge>
                      </div>
                    )}
                    <CardContent className="p-5 text-center space-y-3">
                      <div className="text-3xl font-black text-amber-600">{opt.amount}</div>
                      <div className="text-xs text-amber-600">크레딧</div>
                      <div className="text-xl font-black text-slate-900">{opt.price}</div>
                      <div className="text-xs text-slate-400">{opt.perCredit}</div>
                      <Button
                        className={`w-full ${opt.popular ? "bg-gradient-to-r from-blue-600 to-indigo-600" : opt.best ? "bg-gradient-to-r from-green-600 to-emerald-600" : ""}`}
                        variant={opt.popular || opt.best ? "default" : "outline"}
                        onClick={() => navigate("/login")}
                      >
                        구매하기
                      </Button>
                    </CardContent>
                  </Card>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* FAQ */}
        <div className="max-w-3xl mx-auto space-y-4">
          <h2 className="text-2xl font-black text-slate-900 text-center mb-6">자주 묻는 질문</h2>
          {[
            { q: "무료 플랜에서 유료 플랜으로 업그레이드하면 기존 데이터는 어떻게 되나요?", a: "기존의 모든 지원 건 데이터, 분석 결과, 면접 기록이 그대로 유지됩니다. 업그레이드 즉시 새로운 기능을 사용할 수 있습니다." },
            { q: "언제든지 해지할 수 있나요?", a: "네, 언제든지 해지할 수 있습니다. 해지 후에도 남은 결제 기간 동안은 서비스를 계속 이용하실 수 있습니다." },
            { q: "크레딧은 만료되나요?", a: "구매한 크레딧은 구매일로부터 1년간 유효합니다. 만료 30일 전에 알림을 발송해 드립니다." },
            { q: "환불 정책은 어떻게 되나요?", a: "결제일로부터 7일 이내, AI 기능을 사용하지 않은 경우 전액 환불 가능합니다. 자세한 내용은 이용약관을 참조해주세요." },
          ].map((item, i) => (
            <div key={i} className="bg-white border border-slate-200 rounded-xl p-5">
              <div className="font-bold text-slate-800 text-sm mb-2">Q. {item.q}</div>
              <div className="text-sm text-slate-600 leading-relaxed">A. {item.a}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
