export interface CreditBalanceChangedDetail {
  remainingCredit?: number;
}

const CREDIT_BALANCE_CHANGED = "careertuner:credit-balance-changed";

export function publishCreditBalanceChanged(remainingCredit?: number) {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent<CreditBalanceChangedDetail>(
    CREDIT_BALANCE_CHANGED,
    { detail: { remainingCredit } },
  ));
}

export function subscribeCreditBalanceChanged(
  listener: (detail: CreditBalanceChangedDetail) => void,
) {
  if (typeof window === "undefined") return () => undefined;
  const handler = (event: Event) => {
    listener((event as CustomEvent<CreditBalanceChangedDetail>).detail ?? {});
  };
  window.addEventListener(CREDIT_BALANCE_CHANGED, handler);
  return () => window.removeEventListener(CREDIT_BALANCE_CHANGED, handler);
}
