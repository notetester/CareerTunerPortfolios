export type AdminCreditTransactionType = "AI_USAGE" | "CHARGE" | "REFUND" | "ADMIN_ADJUST";

export interface AdminCreditTransactionRow {
  id: number;
  userId: number;
  userEmail: string;
  userName: string;
  type: AdminCreditTransactionType;
  amount: number;
  balanceAfter: number;
  featureType: string | null;
  aiUsageLogId: number | null;
  reason: string | null;
  createdAt: string;
}

export interface AdminCreditPage {
  items: AdminCreditTransactionRow[];
  total: number;
  page: number;
  size: number;
}

export interface AdminCreditSummary {
  totalTransactions: number;
  adminAdjustmentCount: number;
  totalGranted: number;
  totalDeducted: number;
  totalUserBalance: number;
}

export interface AdminCreditFilters {
  keyword?: string;
  userId?: number;
  type?: AdminCreditTransactionType;
  page?: number;
  size?: number;
}

export interface AdminCreditAdjustRequest {
  userId: number;
  amount: number;
  reason: string;
  requestId: string;
}

export interface AdminCreditAdjustResponse {
  transactionId: number;
  userId: number;
  amount: number;
  balanceBefore: number;
  balanceAfter: number;
}
