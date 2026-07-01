// 데모/목: 결제·구독·크레딧 도메인.
// 김데모(PRO, 50 크레딧) 기준으로 활성 구독·결제 내역·크레딧 히스토리·사용량·구매 상품을 채운다.
// 모든 응답 타입은 billingApi 에서 그대로 export 되므로 그 타입으로 직접 검증한다(transform 없음).
import type { MockRoute, MockContext } from "../registry";
import { iso } from "../registry";
import type {
  SubscriptionPlan,
  CreditProduct,
  MyBilling,
  Payment,
  UsageRow,
  CreditTransaction,
  BillingCycle,
} from "@/features/billing/api/billingApi";
import type { CurrentRefundPolicy } from "@/features/billing/api/refundPolicyApi";
import type { RefundReasonCode, RefundRequestRow } from "@/features/billing/api/refundRequestApi";

// ── 공개 정보: 요금제 목록 ──
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

// ── 공개 정보: 크레딧 충전 상품 ──
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
    enabled: true,
    sortOrder: 4,
  },
];

// ── 내 구독 상태(세션 내 in-memory). 구독/해지/충전 시 갱신해 화면에 즉시 반영한다. ──
const myBilling: MyBilling = {
  currentPlanCode: "PRO",
  currentPlanName: "프로",
  subscriptionStatus: "ACTIVE",
  periodEnd: iso(-18), // 18일 뒤 갱신
  creditBalance: 50,
};

// ── 결제 내역 ──
const payments: Payment[] = [
  {
    id: 5001,
    provider: "TOSS",
    productType: "SUBSCRIPTION",
    productCode: "PLAN_PRO",
    orderId: "ORD-20260601-PRO-5001",
    amount: 19900,
    plan: "프로",
    creditAmount: null,
    status: "PAID",
    paidAt: iso(12),
    createdAt: iso(12),
  },
  {
    id: 5002,
    provider: "TOSS",
    productType: "CREDIT",
    productCode: "CREDIT_100",
    orderId: "ORD-20260605-C100-5002",
    amount: 9000,
    plan: null,
    creditAmount: 100,
    status: "PAID",
    paidAt: iso(8),
    createdAt: iso(8),
  },
  {
    id: 5003,
    provider: "KAKAOPAY",
    productType: "CREDIT",
    productCode: "CREDIT_30",
    orderId: "ORD-20260610-C30-5003",
    amount: 3000,
    plan: null,
    creditAmount: 30,
    status: "PAID",
    paidAt: iso(3),
    createdAt: iso(3),
  },
];

// ── 이번 달 AI 사용량 ──
const usage: UsageRow[] = [
  { featureType: "JOB_ANALYSIS", used: 24, creditUsed: 24 },
  { featureType: "COMPANY_ANALYSIS", used: 12, creditUsed: 12 },
  { featureType: "FIT_ANALYSIS", used: 9, creditUsed: 18 },
  { featureType: "INTERVIEW_QUESTION", used: 16, creditUsed: 32 },
  { featureType: "INTERVIEW_ANSWER_EVAL", used: 21, creditUsed: 21 },
  { featureType: "INTERVIEW_REPORT", used: 5, creditUsed: 15 },
  { featureType: "CAREER_TREND", used: 3, creditUsed: 9 },
];

// ── 크레딧 거래 내역(최신순) ──
const creditTransactions: CreditTransaction[] = [
  { id: 6001, type: "GRANT", amount: 100, balanceAfter: 100, reason: "프로 플랜 월 지급", createdAt: iso(12) },
  { id: 6002, type: "USE", amount: -2, balanceAfter: 98, reason: "공고 분석 - 카카오", createdAt: iso(11) },
  { id: 6003, type: "CHARGE", amount: 100, balanceAfter: 198, reason: "크레딧 100 충전", createdAt: iso(8) },
  { id: 6004, type: "USE", amount: -2, balanceAfter: 196, reason: "예상 질문 생성 - 네이버", createdAt: iso(6) },
  { id: 6005, type: "USE", amount: -1, balanceAfter: 195, reason: "면접 답변 평가 - 토스", createdAt: iso(5) },
  { id: 6006, type: "CHARGE", amount: 30, balanceAfter: 225, reason: "크레딧 30 충전", createdAt: iso(3) },
  { id: 6007, type: "USE", amount: -3, balanceAfter: 222, reason: "적합도 분석 - 라인", createdAt: iso(2) },
];

const currentRefundPolicy: CurrentRefundPolicy = {
  id: 1,
  policyCode: "REFUND_DEFAULT",
  version: 1,
  title: "환불 정책",
  summary: "결제 후 7일 이내 미사용 결제 건은 전액 환불 검토가 가능하며, 크레딧 또는 사용권 사용 시 환불이 제한됩니다.",
  content: "환불 신청은 관리자가 결제 및 사용 이력을 확인한 뒤 전액 환불 또는 환불 불가로 처리합니다.",
  rulesJson: JSON.stringify({ withdrawalDays: 7, unusedPolicy: "FULL_REFUND", usedPolicy: "NO_REFUND" }),
  effectiveAt: "2026-01-01T00:00:00",
  noticeId: 1,
  acknowledgedTriggers: [],
};

const refundRequests: RefundRequestRow[] = [];

interface SubscribeBody {
  planCode?: string;
  cycle?: BillingCycle;
}

interface PurchaseBody {
  productCode?: string;
}

export const billingRoutes: MockRoute[] = [
  // ── 공개: 요금제 / 크레딧 상품 ──
  { method: "GET", pattern: /^\/billing\/plans$/, handler: () => plans },
  { method: "GET", pattern: /^\/billing\/credit-products$/, handler: () => creditProducts },

  // ── 내 결제/구독 현황 ──
  { method: "GET", pattern: /^\/billing\/me$/, handler: () => ({ ...myBilling }) },
  { method: "GET", pattern: /^\/billing\/payments$/, handler: () => [...payments] },
  { method: "GET", pattern: /^\/billing\/usage$/, handler: () => [...usage] },
  { method: "GET", pattern: /^\/billing\/credit-transactions$/, handler: () => [...creditTransactions] },
  { method: "GET", pattern: /^\/billing\/refund-policy\/current$/, handler: () => ({ ...currentRefundPolicy }) },
  { method: "POST", pattern: /^\/billing\/refund-policy\/acknowledgements$/, handler: () => ({ ...currentRefundPolicy }) },
  { method: "GET", pattern: /^\/billing\/refunds$/, handler: () => [...refundRequests] },
  {
    method: "POST",
    pattern: /^\/billing\/refunds$/,
    handler: ({ body }: MockContext) => {
      const request = body as { paymentId?: number; reasonCode?: RefundReasonCode; reasonText?: string } | undefined;
      const payment = payments.find((row) => row.id === request?.paymentId);
      if (!payment) return null;
      const row: RefundRequestRow = {
        id: 7000 + refundRequests.length + 1,
        paymentId: payment.id,
        userId: 1,
        userEmail: "demo@careertuner.test",
        userName: "김데모",
        orderId: payment.orderId,
        productType: payment.productType,
        productCode: payment.productCode,
        plan: payment.plan,
        paymentAmount: payment.amount,
        paidAt: payment.paidAt,
        paymentStatus: payment.status,
        status: "REQUESTED",
        reasonCode: request?.reasonCode ?? "CHANGE_OF_MIND",
        reasonText: request?.reasonText ?? null,
        eligibilityResult: "ELIGIBLE",
        creditUsed: false,
        benefitUsed: false,
        refundAmount: payment.amount,
        decisionBasisJson: "{}",
        reviewedBy: null,
        reviewedReason: null,
        requestedAt: new Date().toISOString(),
        reviewedAt: null,
      };
      refundRequests.unshift(row);
      return row;
    },
  },

  // ── 구독 신청: planCode 로 현재 플랜을 갱신하고 갱신된 MyBilling 반환 ──
  {
    method: "POST",
    pattern: /^\/billing\/subscribe$/,
    handler: ({ body }: MockContext) => {
      const request = body as SubscribeBody | undefined;
      const target = plans.find((plan) => plan.code === request?.planCode);
      if (target) {
        myBilling.currentPlanCode = target.code;
        myBilling.currentPlanName = target.name;
        myBilling.subscriptionStatus = target.code === "FREE" ? "FREE" : "ACTIVE";
        myBilling.periodEnd = target.code === "FREE" ? null : iso(-30);
      }
      return { ...myBilling };
    },
  },

  // ── 크레딧 충전: productCode 의 creditAmount 만큼 잔액 증가 후 MyBilling 반환 ──
  {
    method: "POST",
    pattern: /^\/billing\/credits\/purchase$/,
    handler: ({ body }: MockContext) => {
      const request = body as PurchaseBody | undefined;
      const product = creditProducts.find((item) => item.code === request?.productCode);
      if (product) {
        myBilling.creditBalance += product.creditAmount;
        creditTransactions.unshift({
          id: 6000 + creditTransactions.length + 1,
          type: "CHARGE",
          amount: product.creditAmount,
          balanceAfter: myBilling.creditBalance,
          reason: `${product.name} 충전`,
          createdAt: new Date().toISOString(),
        });
        payments.unshift({
          id: 5000 + payments.length + 1,
          provider: "TOSS",
          productType: "CREDIT",
          productCode: product.code,
          orderId: `ORD-DEMO-${product.code}-${5000 + payments.length + 1}`,
          amount: product.price,
          plan: null,
          creditAmount: product.creditAmount,
          status: "PAID",
          paidAt: new Date().toISOString(),
          createdAt: new Date().toISOString(),
        });
      }
      return { ...myBilling };
    },
  },

  // ── 구독 해지: 상태를 CANCELED 로 바꾸고 만료 예정일은 유지 ──
  {
    method: "POST",
    pattern: /^\/billing\/subscription\/cancel$/,
    handler: () => {
      myBilling.subscriptionStatus = "CANCELED";
      return { ...myBilling };
    },
  },
];
