// 데모/목: 관리자 결제·요금제 운영(ADMIN BILLING).
// 운영자 관점의 플랫폼 전체 결제 내역/매출 집계와 요금제·크레딧 상품 구성을 채운다.
// 응답 타입은 admin/features/billing/api.ts 가 api<T>(path) 로 기대하는 T 그대로(transform 없음).
//   GET /admin/payments            -> AdminPaymentRow[]   (?status= 필터)
//   GET /admin/payments/summary    -> AdminPaymentSummary
//   GET /admin/plans               -> AdminPlans { plans, creditProducts, benefitPolicies, featureBenefitPolicies }
//   GET /admin/plans/policy-changes
//   POST /admin/plans/policy-changes
//   POST /admin/plans/policy-changes/{id}/cancel
import type { MockRoute, MockContext } from "../../registry";
import { iso } from "../../registry";
import type { SubscriptionPlan, CreditProduct } from "@/features/billing/api/billingApi";
import type {
  AdminPaymentRow,
  AdminPaymentSummary,
  AdminPlans,
  AiFeatureBenefitPolicy,
  BillingPolicyChange,
  CreateBillingPolicyChangeRequest,
  SubscriptionBenefitPolicy,
} from "@/admin/features/billing/api";

// ── 데모 회원 결제: 김데모(9001) 외 다른 구직자/플랜 결제도 섞어 플랫폼 전체 운영 화면을 만든다. ──
const payments: AdminPaymentRow[] = [
  {
    id: 5101,
    userId: 9001,
    userEmail: "demo@careertuner.dev",
    userName: "김데모",
    provider: "TOSS",
    productCode: "PLAN_PRO",
    amount: 19900,
    plan: "프로",
    creditAmount: null,
    status: "PAID",
    paidAt: iso(12),
    createdAt: iso(12),
  },
  {
    id: 5102,
    userId: 9001,
    userEmail: "demo@careertuner.dev",
    userName: "김데모",
    provider: "TOSS",
    productCode: "CREDIT_100",
    amount: 9000,
    plan: null,
    creditAmount: 100,
    status: "PAID",
    paidAt: iso(8),
    createdAt: iso(8),
  },
  {
    id: 5103,
    userId: 9002,
    userEmail: "jiwon.park@example.com",
    userName: "박지원",
    provider: "KAKAOPAY",
    productCode: "PLAN_BASIC",
    amount: 9900,
    plan: "베이직",
    creditAmount: null,
    status: "PAID",
    paidAt: iso(6),
    createdAt: iso(6),
  },
  {
    id: 5104,
    userId: 9003,
    userEmail: "minseo.choi@example.com",
    userName: "최민서",
    provider: "KAKAOPAY",
    productCode: "CREDIT_30",
    amount: 3000,
    plan: null,
    creditAmount: 30,
    status: "PAID",
    paidAt: iso(4),
    createdAt: iso(4),
  },
  {
    id: 5105,
    userId: 9004,
    userEmail: "hyun.kang@example.com",
    userName: "강현",
    provider: "TOSS",
    productCode: "PLAN_PREMIUM",
    amount: 39900,
    plan: "프리미엄",
    creditAmount: null,
    status: "READY",
    paidAt: null,
    createdAt: iso(2),
  },
  {
    id: 5106,
    userId: 9002,
    userEmail: "jiwon.park@example.com",
    userName: "박지원",
    provider: "TOSS",
    productCode: "CREDIT_300",
    amount: 24000,
    plan: null,
    creditAmount: 300,
    status: "CANCELED",
    paidAt: null,
    createdAt: iso(1),
  },
];

// ── 매출 집계: PAID 건만 누적 매출에 합산. ──
const summary: AdminPaymentSummary = {
  totalCount: payments.length,
  paidCount: payments.filter((p) => p.status === "PAID").length,
  totalRevenue: payments
    .filter((p) => p.status === "PAID")
    .reduce((sum, p) => sum + p.amount, 0),
};

// ── 요금제 구성(공개 요금제와 동일 세트). ──
const plans: SubscriptionPlan[] = [
  {
    id: 1,
    code: "FREE",
    name: "무료",
    monthlyPrice: 0,
    yearlyPrice: 0,
    description: "취업 준비를 가볍게 시작하는 기본 플랜",
    active: true,
    sortOrder: 1,
  },
  {
    id: 2,
    code: "BASIC",
    name: "베이직",
    monthlyPrice: 9900,
    yearlyPrice: 8250,
    description: "공고 분석과 텍스트 면접을 충분히 활용하는 플랜",
    active: true,
    sortOrder: 2,
  },
  {
    id: 3,
    code: "PRO",
    name: "프로",
    monthlyPrice: 19900,
    yearlyPrice: 16500,
    description: "음성 면접과 장기 취업 분석까지 무제한으로 쓰는 플랜",
    active: true,
    sortOrder: 3,
  },
  {
    id: 4,
    code: "PREMIUM",
    name: "프리미엄",
    monthlyPrice: 39900,
    yearlyPrice: 33250,
    description: "아바타 면접관·영상 분석·1:1 전략 컨설팅까지 제공",
    active: true,
    sortOrder: 4,
  },
];

// ── 크레딧 충전 상품 구성. ──
const creditProducts: CreditProduct[] = [
  {
    id: 1,
    code: "CREDIT_30",
    name: "크레딧 30",
    price: 3000,
    creditAmount: 30,
    description: "가볍게 충전하는 소량 패키지",
    badge: null,
    enabled: true,
    sortOrder: 1,
  },
  {
    id: 2,
    code: "CREDIT_100",
    name: "크레딧 100",
    price: 9000,
    creditAmount: 100,
    description: "면접 준비에 가장 많이 쓰는 인기 패키지",
    badge: "인기",
    enabled: true,
    sortOrder: 2,
  },
  {
    id: 3,
    code: "CREDIT_300",
    name: "크레딧 300",
    price: 24000,
    creditAmount: 300,
    description: "여러 지원 건을 동시에 준비할 때 추천",
    badge: "20% 추가",
    enabled: true,
    sortOrder: 3,
  },
  {
    id: 4,
    code: "CREDIT_500",
    name: "크레딧 500",
    price: 36000,
    creditAmount: 500,
    description: "장기 취업 준비를 위한 대용량 패키지",
    badge: "최대 혜택",
    enabled: false,
    sortOrder: 4,
  },
];

const benefitPolicies: SubscriptionBenefitPolicy[] = [
  {
    planCode: "FREE",
    benefitCode: "APPLICATION_ANALYSIS",
    benefitName: "지원 건 분석",
    benefitType: "APPLICATION_CASE",
    quantity: 3,
    resetCycle: "MONTHLY",
    overagePolicy: "CREDIT",
    creditCost: 3,
    active: true,
    sortOrder: 1,
  },
  {
    planCode: "BASIC",
    benefitCode: "APPLICATION_ANALYSIS",
    benefitName: "지원 건 분석",
    benefitType: "APPLICATION_CASE",
    quantity: 20,
    resetCycle: "MONTHLY",
    overagePolicy: "CREDIT",
    creditCost: 2,
    active: true,
    sortOrder: 1,
  },
  {
    planCode: "PRO",
    benefitCode: "CORRECTION",
    benefitName: "첨삭 사용권",
    benefitType: "CORRECTION",
    quantity: 40,
    resetCycle: "MONTHLY",
    overagePolicy: "CREDIT",
    creditCost: 2,
    active: true,
    sortOrder: 2,
  },
];

const featureBenefitPolicies: AiFeatureBenefitPolicy[] = [
  {
    featureType: "JOB_ANALYSIS",
    benefitCode: "APPLICATION_ANALYSIS",
    chargeUnit: "APPLICATION_CASE",
    includedInTicket: true,
    defaultCreditCost: 3,
    minCreditCost: 1,
    maxCreditCost: 5,
    creditUnitTokens: 1000,
    active: true,
  },
  {
    featureType: "CORRECTION",
    benefitCode: "CORRECTION",
    chargeUnit: "DOCUMENT",
    includedInTicket: true,
    defaultCreditCost: 2,
    minCreditCost: 2,
    maxCreditCost: 5,
    creditUnitTokens: 1000,
    active: true,
  },
];

let policyChangeSeq = 7000;
const policyChanges: BillingPolicyChange[] = [];

function policyTargetCode(snapshot: Record<string, unknown>) {
  if (snapshot.planCode && snapshot.benefitCode) return `${snapshot.planCode}:${snapshot.benefitCode}`;
  if (snapshot.featureType) return String(snapshot.featureType).toUpperCase();
  if (snapshot.code) return String(snapshot.code).toUpperCase();
  return "UNKNOWN";
}

function currentPolicySnapshot(targetType: string, snapshot: Record<string, unknown>) {
  if (targetType === "SUBSCRIPTION_PLAN") {
    return plans.find((plan) => plan.code === String(snapshot.code ?? "").toUpperCase()) ?? {};
  }
  if (targetType === "CREDIT_PRODUCT") {
    return creditProducts.find((product) => product.code === String(snapshot.code ?? "").toUpperCase()) ?? {};
  }
  if (targetType === "SUBSCRIPTION_BENEFIT_POLICY") {
    const planCode = String(snapshot.planCode ?? "").toUpperCase();
    const benefitCode = String(snapshot.benefitCode ?? "").toUpperCase();
    return benefitPolicies.find((policy) => policy.planCode === planCode && policy.benefitCode === benefitCode) ?? {};
  }
  if (targetType === "AI_FEATURE_BENEFIT_POLICY") {
    return featureBenefitPolicies.find((policy) => policy.featureType === String(snapshot.featureType ?? "").toUpperCase()) ?? {};
  }
  return {};
}

export const adminBillingRoutes: MockRoute[] = [
  // ── 결제 내역: ?status= 로 필터(없으면 전체, 최신순). ──
  {
    method: "GET",
    pattern: /^\/admin\/payments$/,
    handler: ({ query }: MockContext): AdminPaymentRow[] => {
      const status = query.get("status");
      const rows = status ? payments.filter((p) => p.status === status) : [...payments];
      return rows.slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
    },
  },

  // ── 매출 집계 카드. ──
  {
    method: "GET",
    pattern: /^\/admin\/payments\/summary$/,
    handler: (): AdminPaymentSummary => ({ ...summary }),
  },

  // ── 요금제·크레딧 상품 구성. ──
  {
    method: "GET",
    pattern: /^\/admin\/plans$/,
    handler: (): AdminPlans => ({
      plans: [...plans],
      creditProducts: [...creditProducts],
      benefitPolicies: [...benefitPolicies],
      featureBenefitPolicies: [...featureBenefitPolicies],
    }),
  },
  {
    method: "GET",
    pattern: /^\/admin\/plans\/policy-changes$/,
    handler: (): BillingPolicyChange[] => [...policyChanges].sort((a, b) => b.id - a.id),
  },
  {
    method: "POST",
    pattern: /^\/admin\/plans\/policy-changes$/,
    handler: ({ body }: MockContext): BillingPolicyChange => {
      const request = body as CreateBillingPolicyChangeRequest;
      const nextSnapshot = request.nextSnapshot ?? {};
      const now = new Date().toISOString();
      const change: BillingPolicyChange = {
        id: ++policyChangeSeq,
        targetType: request.targetType,
        targetCode: policyTargetCode(nextSnapshot),
        currentSnapshotJson: JSON.stringify(currentPolicySnapshot(request.targetType, nextSnapshot)),
        nextSnapshotJson: JSON.stringify(nextSnapshot),
        effectiveFrom: request.effectiveFrom,
        applyMode: request.applyMode,
        status: "SCHEDULED",
        createdBy: 1,
        createdAt: now,
        canceledBy: null,
        canceledAt: null,
      };
      policyChanges.unshift(change);
      return change;
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/plans\/policy-changes\/(\d+)\/cancel$/,
    handler: ({ params }: MockContext): BillingPolicyChange => {
      const id = Number(params[0]);
      const target = policyChanges.find((change) => change.id === id);
      if (!target) throw new Error("예약 변경을 찾을 수 없습니다.");
      target.status = "CANCELED";
      target.canceledBy = 1;
      target.canceledAt = new Date().toISOString();
      return target;
    },
  },
];
