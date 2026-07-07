import { api } from "@/app/lib/api";

export interface RewardRule {
  id: number;
  eventCode: string;
  name: string;
  pointAmount: number;
  creditAmount: number;
  dailyCap: number | null;
  enabled: boolean;
  description: string | null;
  sortOrder: number;
  updatedAt: string;
}

export interface RewardRuleUpdate {
  name: string;
  pointAmount: number;
  creditAmount: number;
  dailyCap: number | null;
  enabled: boolean;
  description: string | null;
  sortOrder: number;
}

export interface LevelPolicy {
  id: number;
  level: number;
  levelName: string;
  minPoint: number;
  levelupCredit: number;
  levelupCouponCode: string | null;
  benefitNote: string | null;
  active: boolean;
}

export interface LevelPolicyRequest {
  level: number;
  levelName: string;
  minPoint: number;
  levelupCredit: number;
  levelupCouponCode: string | null;
  benefitNote: string | null;
  active: boolean;
}

export interface Coupon {
  id: number;
  code: string;
  name: string;
  discountType: string;
  discountValue: number;
  minPurchase: number;
  validFrom: string | null;
  validUntil: string | null;
  maxIssue: number | null;
  issuedCount: number;
  enabled: boolean;
}

export interface CouponRequest {
  code: string;
  name: string;
  discountType: string;
  discountValue: number;
  minPurchase: number;
  validFrom: string | null;
  validUntil: string | null;
  maxIssue: number | null;
  enabled: boolean;
}

export interface CouponPage {
  items: Coupon[];
  total: number;
  page: number;
  size: number;
}

export interface RewardHistoryRow {
  id: number;
  userId: number;
  userEmail: string;
  userName: string;
  eventCode: string;
  pointDelta: number;
  creditDelta: number;
  levelBefore: number | null;
  levelAfter: number | null;
  refType: string | null;
  refId: number | null;
  reason: string | null;
  createdAt: string;
}

export interface RewardHistoryPage {
  items: RewardHistoryRow[];
  total: number;
  page: number;
  size: number;
}

// ── 적립 규칙 ──
export const getRewardRules = () => api<RewardRule[]>("/admin/rewards/rules");
export const updateRewardRule = (id: number, body: RewardRuleUpdate) =>
  api<RewardRule>(`/admin/rewards/rules/${id}`, { method: "PUT", body: JSON.stringify(body) });
export const toggleRewardRule = (id: number, enabled: boolean) =>
  api<RewardRule>(`/admin/rewards/rules/${id}/enabled?enabled=${enabled}`, { method: "PATCH" });

// ── 레벨 정책 ──
export const getLevelPolicies = () => api<LevelPolicy[]>("/admin/rewards/levels");
export const createLevelPolicy = (body: LevelPolicyRequest) =>
  api<LevelPolicy>("/admin/rewards/levels", { method: "POST", body: JSON.stringify(body) });
export const updateLevelPolicy = (id: number, body: LevelPolicyRequest) =>
  api<LevelPolicy>(`/admin/rewards/levels/${id}`, { method: "PUT", body: JSON.stringify(body) });
export const deleteLevelPolicy = (id: number) =>
  api<void>(`/admin/rewards/levels/${id}`, { method: "DELETE" });

// ── 쿠폰 ──
export const getCoupons = (keyword = "", page = 1, size = 20) => {
  const sp = new URLSearchParams();
  if (keyword) sp.set("keyword", keyword);
  sp.set("page", String(page));
  sp.set("size", String(size));
  return api<CouponPage>(`/admin/rewards/coupons?${sp.toString()}`);
};
export const createCoupon = (body: CouponRequest) =>
  api<Coupon>("/admin/rewards/coupons", { method: "POST", body: JSON.stringify(body) });
export const updateCoupon = (id: number, body: CouponRequest) =>
  api<Coupon>(`/admin/rewards/coupons/${id}`, { method: "PUT", body: JSON.stringify(body) });
export const issueCoupon = (id: number, userId: number) =>
  api<unknown>(`/admin/rewards/coupons/${id}/issue`, { method: "POST", body: JSON.stringify({ userId }) });

// ── 리워드 이력 ──
export const getRewardHistory = (params: {
  userId?: number; eventCode?: string; keyword?: string; page?: number; size?: number;
}) => {
  const sp = new URLSearchParams();
  if (params.userId) sp.set("userId", String(params.userId));
  if (params.eventCode) sp.set("eventCode", params.eventCode);
  if (params.keyword) sp.set("keyword", params.keyword);
  sp.set("page", String(params.page ?? 1));
  sp.set("size", String(params.size ?? 20));
  return api<RewardHistoryPage>(`/admin/rewards/history?${sp.toString()}`);
};
