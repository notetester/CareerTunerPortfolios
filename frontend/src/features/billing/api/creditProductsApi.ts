import { api } from "@/app/lib/api";
import type { CreditProduct } from "../types/billing";

/** DB에 등록된 활성 크레딧 충전 상품 목록을 조회한다. */
export function listCreditProducts(): Promise<CreditProduct[]> {
  return api<CreditProduct[]>("/credit-products", { method: "GET" }, { auth: false });
}
