import { api } from "@/app/lib/api";

export interface RewardHistoryItem {
  id: number;
  eventCode: string;
  pointDelta: number;
  creditDelta: number;
  levelAfter: number | null;
  reason: string | null;
  createdAt: string;
}

export interface MyReward {
  activityPoint: number;
  level: number;
  levelName: string;
  nextLevel: number | null;
  nextLevelName: string | null;
  pointToNextLevel: number | null;
  credit: number;
  recentHistory: RewardHistoryItem[];
}

export interface MyCoupon {
  id: number;
  code: string;
  name: string;
  discountType: string;
  discountValue: number;
  minPurchase: number;
  status: string;
  issuedAt: string;
  usedAt: string | null;
}

export interface CouponRedeemResult {
  code: string;
  discountType: string;
  creditGranted: number;
  balanceAfter: number;
  message: string;
}

export const getMyReward = () => api<MyReward>("/rewards/me");
export const getMyCoupons = () => api<MyCoupon[]>("/coupons/me");
export const redeemCoupon = (code: string) =>
  api<CouponRedeemResult>("/coupons/redeem", { method: "POST", body: JSON.stringify({ code }) });
