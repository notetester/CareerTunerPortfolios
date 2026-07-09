import type { SubscriptionBenefitPolicy, SubscriptionPlan } from "../types/billing";

export type PlanCode = "FREE" | "BASIC" | "PRO" | "PREMIUM";

export interface DisplayBenefit {
  code: string;
  label: string;
  text: string;
  quantity: number;
  disabled: boolean;
  premium: boolean;
}

export interface DisplayPlan {
  code: string;
  name: string;
  description: string;
  monthlyPrice: string;
  yearlyPrice: string;
  period: string;
  badge?: string;
  highlighted: boolean;
  cta: string;
  benefits: DisplayBenefit[];
}

const benefitOrder = [
  "APPLICATION_ANALYSIS",
  "MOCK_INTERVIEW",
  "VOICE_INTERVIEW",
  "VIDEO_ANALYSIS",
  "AVATAR_INTERVIEW",
] as const;

const fallbackPlans: SubscriptionPlan[] = [
  {
    code: "FREE",
    name: "무료",
    monthlyPrice: 0,
    yearlyPrice: 0,
    description: "기본 체험",
    sortOrder: 10,
    benefits: [
      benefit("FREE", "APPLICATION_ANALYSIS", "지원건 분석권", 3, 10),
      benefit("FREE", "MOCK_INTERVIEW", "모의면접권", 1, 20),
      benefit("FREE", "VOICE_INTERVIEW", "음성면접권", 0, 30),
      benefit("FREE", "VIDEO_ANALYSIS", "영상분석권", 0, 40),
      benefit("FREE", "AVATAR_INTERVIEW", "아바타면접권", 0, 50),
    ],
  },
  {
    code: "BASIC",
    name: "베이직",
    monthlyPrice: 9900,
    yearlyPrice: 7900,
    description: "가벼운 취업 준비",
    sortOrder: 20,
    benefits: [
      benefit("BASIC", "APPLICATION_ANALYSIS", "지원건 분석권", 20, 10),
      benefit("BASIC", "MOCK_INTERVIEW", "모의면접권", 10, 20),
      benefit("BASIC", "VOICE_INTERVIEW", "음성면접권", 0, 30),
      benefit("BASIC", "VIDEO_ANALYSIS", "영상분석권", 0, 40),
      benefit("BASIC", "AVATAR_INTERVIEW", "아바타면접권", 0, 50),
    ],
  },
  {
    code: "PRO",
    name: "프로",
    monthlyPrice: 29000,
    yearlyPrice: 23000,
    description: "실전 취업 준비",
    sortOrder: 30,
    benefits: [
      benefit("PRO", "APPLICATION_ANALYSIS", "지원건 분석권", 60, 10),
      benefit("PRO", "MOCK_INTERVIEW", "모의면접권", 30, 20),
      benefit("PRO", "VOICE_INTERVIEW", "음성면접권", 5, 30),
      benefit("PRO", "VIDEO_ANALYSIS", "영상분석권", 1, 40),
      benefit("PRO", "AVATAR_INTERVIEW", "아바타면접권", 0, 50),
    ],
  },
  {
    code: "PREMIUM",
    name: "프리미엄",
    monthlyPrice: 49000,
    yearlyPrice: 39000,
    description: "고급 면접 패키지",
    sortOrder: 40,
    benefits: [
      benefit("PREMIUM", "APPLICATION_ANALYSIS", "지원건 분석권", 150, 10),
      benefit("PREMIUM", "MOCK_INTERVIEW", "모의면접권", 60, 20),
      benefit("PREMIUM", "VOICE_INTERVIEW", "음성면접권", 15, 30),
      benefit("PREMIUM", "VIDEO_ANALYSIS", "영상분석권", 5, 40),
      benefit("PREMIUM", "AVATAR_INTERVIEW", "아바타면접권", 5, 50),
    ],
  },
];

function benefit(
  planCode: string,
  benefitCode: string,
  benefitName: string,
  quantity: number,
  sortOrder: number,
): SubscriptionBenefitPolicy {
  return {
    planCode,
    benefitCode,
    benefitName,
    benefitType: "TICKET",
    quantity,
    resetCycle: "MONTHLY",
    overagePolicy: quantity > 0 ? "BLOCK" : "UPGRADE",
    creditCost: 0,
    sortOrder,
  };
}

function formatCurrency(value: number) {
  return `${value.toLocaleString("ko-KR")}원`;
}

function formatBenefitText(planCode: string, code: string, quantity: number) {
  if (quantity <= 0) return "없음";
  if (planCode === "PRO" && code === "VIDEO_ANALYSIS" && quantity === 1) return "월 1장 체험";
  return `월 ${quantity.toLocaleString("ko-KR")}장`;
}

function isPremiumBenefit(code: string, quantity: number) {
  return quantity > 0 && (code === "VIDEO_ANALYSIS" || code === "AVATAR_INTERVIEW");
}

function sortBenefits(benefits: SubscriptionBenefitPolicy[]) {
  return [...benefits].sort((a, b) => {
    const ai = benefitOrder.indexOf(a.benefitCode as (typeof benefitOrder)[number]);
    const bi = benefitOrder.indexOf(b.benefitCode as (typeof benefitOrder)[number]);
    const ao = ai === -1 ? 999 : ai;
    const bo = bi === -1 ? 999 : bi;
    return ao - bo || a.sortOrder - b.sortOrder;
  });
}

export function subscriptionFallbackPlans() {
  return fallbackPlans;
}

export function toDisplayPlans(plans: SubscriptionPlan[]): DisplayPlan[] {
  return [...plans]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((plan) => {
      const code = plan.code.toUpperCase();
      return {
        code,
        name: plan.name,
        description: plan.description ?? "",
        monthlyPrice: formatCurrency(plan.monthlyPrice),
        yearlyPrice: plan.yearlyPrice == null ? formatCurrency(plan.monthlyPrice) : formatCurrency(plan.yearlyPrice),
        period: code === "FREE" ? "영구 무료" : "월",
        badge: code === "PRO" ? "인기" : undefined,
        highlighted: code === "PRO",
        cta: code === "FREE" ? "무료 시작" : "시작하기",
        benefits: sortBenefits(plan.benefits).map((item) => ({
          code: item.benefitCode,
          label: item.benefitName,
          text: formatBenefitText(code, item.benefitCode, item.quantity),
          quantity: item.quantity,
          disabled: item.quantity <= 0,
          premium: isPremiumBenefit(item.benefitCode, item.quantity),
        })),
      };
    });
}
