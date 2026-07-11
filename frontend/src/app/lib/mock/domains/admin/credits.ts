import type { MockContext, MockRoute } from "../../registry";
import { iso } from "../../registry";
import type {
  AdminCreditAdjustRequest,
  AdminCreditAdjustResponse,
  AdminCreditPage,
  AdminCreditSummary,
  AdminCreditTransactionRow,
} from "@/admin/features/credits/types";

const users = new Map<number, { email: string; name: string; balance: number }>([
  [9001, { email: "demo@careertuner.dev", name: "김데모", balance: 118 }],
  [9002, { email: "jiwon.park@example.com", name: "박지원", balance: 42 }],
  [9003, { email: "minseo.choi@example.com", name: "최민서", balance: 76 }],
]);

let transactionSequence = 9206;
const adjustmentRequests = new Map<string, {
  amount: number;
  reason: string;
  response: AdminCreditAdjustResponse;
}>();
const transactions: AdminCreditTransactionRow[] = [
  { id: 9206, userId: 9001, userEmail: "demo@careertuner.dev", userName: "김데모", type: "AI_USAGE", amount: -2, balanceAfter: 118, featureType: "CORRECTION_SELF_INTRO", aiUsageLogId: 8303, reason: "자기소개서 첨삭", createdAt: iso(0) },
  { id: 9205, userId: 9002, userEmail: "jiwon.park@example.com", userName: "박지원", type: "ADMIN_ADJUST", amount: 10, balanceAfter: 42, featureType: "ADMIN_CREDIT_ADJUST", aiUsageLogId: null, reason: "서비스 장애 보상", createdAt: iso(1) },
  { id: 9204, userId: 9001, userEmail: "demo@careertuner.dev", userName: "김데모", type: "CHARGE", amount: 100, balanceAfter: 120, featureType: null, aiUsageLogId: null, reason: "크레딧 100 충전", createdAt: iso(2) },
  { id: 9203, userId: 9003, userEmail: "minseo.choi@example.com", userName: "최민서", type: "REFUND", amount: 30, balanceAfter: 76, featureType: null, aiUsageLogId: null, reason: "가결제 전액 환불", createdAt: iso(3) },
  { id: 9202, userId: 9002, userEmail: "jiwon.park@example.com", userName: "박지원", type: "AI_USAGE", amount: -2, balanceAfter: 32, featureType: "CORRECTION_INTERVIEW_ANSWER", aiUsageLogId: 8302, reason: "면접 답변 첨삭", createdAt: iso(4) },
];

function summary(): AdminCreditSummary {
  return {
    totalTransactions: transactions.length,
    adminAdjustmentCount: transactions.filter((row) => row.type === "ADMIN_ADJUST").length,
    totalGranted: transactions.filter((row) => row.amount > 0).reduce((sum, row) => sum + row.amount, 0),
    totalDeducted: transactions.filter((row) => row.amount < 0).reduce((sum, row) => sum - row.amount, 0),
    totalUserBalance: [...users.values()].reduce((sum, user) => sum + user.balance, 0),
  };
}

export const adminCreditRoutes: MockRoute[] = [
  { method: "GET", pattern: /^\/admin\/credits\/summary$/, handler: () => summary() },
  {
    method: "POST", pattern: /^\/admin\/credits\/adjust$/, handler: ({ body }: MockContext): AdminCreditAdjustResponse => {
      const request = body as AdminCreditAdjustRequest;
      const user = users.get(Number(request?.userId));
      if (!user) throw new Error("회원을 찾을 수 없습니다.");
      if (!Number.isInteger(request.amount) || request.amount === 0 || Math.abs(request.amount) > 1_000_000) throw new Error("조정 크레딧 값이 올바르지 않습니다.");
      if (!request.reason?.trim()) throw new Error("조정 사유는 필수입니다.");
      const requestKey = request.requestId ? `${request.userId}:${request.requestId}` : null;
      const previous = requestKey ? adjustmentRequests.get(requestKey) : null;
      if (previous) {
        if (previous.amount !== request.amount || previous.reason !== request.reason.trim()) throw new Error("동일 요청 ID가 다른 크레딧 조정 내용에 사용되었습니다.");
        return previous.response;
      }
      const balanceBefore = user.balance;
      const balanceAfter = balanceBefore + request.amount;
      if (balanceAfter < 0) throw new Error("차감할 크레딧이 부족합니다.");
      user.balance = balanceAfter;
      const transactionId = ++transactionSequence;
      transactions.unshift({ id: transactionId, userId: request.userId, userEmail: user.email, userName: user.name, type: "ADMIN_ADJUST", amount: request.amount, balanceAfter, featureType: "ADMIN_CREDIT_ADJUST", aiUsageLogId: null, reason: request.reason.trim(), createdAt: new Date().toISOString() });
      const response = { transactionId, userId: request.userId, amount: request.amount, balanceBefore, balanceAfter };
      if (requestKey) adjustmentRequests.set(requestKey, { amount: request.amount, reason: request.reason.trim(), response });
      return response;
    },
  },
  {
    method: "GET", pattern: /^\/admin\/credits$/, handler: ({ query }: MockContext): AdminCreditPage => {
      const keyword = (query.get("keyword") ?? "").trim().toLowerCase();
      const userId = Number(query.get("userId")) || null;
      const type = query.get("type");
      const page = Math.max(1, Number(query.get("page") ?? 1) || 1);
      const size = Math.min(100, Math.max(1, Number(query.get("size") ?? 20) || 20));
      const filtered = transactions.filter((row) => {
        const haystack = [row.userEmail, row.userName, row.reason, row.featureType].filter(Boolean).join(" ").toLowerCase();
        return (!keyword || haystack.includes(keyword)) && (!userId || row.userId === userId) && (!type || row.type === type);
      });
      const offset = (page - 1) * size;
      return { items: filtered.slice(offset, offset + size), total: filtered.length, page, size };
    },
  },
];
