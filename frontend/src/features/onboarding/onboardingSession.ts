import type { ConsentRequest } from "@/app/auth/consentApi";

const CONSENTS_KEY = "careertuner.onboarding.pendingConsents";
const RETURN_TO_KEY = "careertuner.onboarding.returnTo";
// Root가 OnboardingFlow를 렌더하도록 `ob` 플래그를 유지한 채 인증 복귀 단계를 전달한다.
const DEFAULT_RETURN_TO = "/?ob&obResume=billing";

function isSafeInternalPath(value: string | null): value is string {
  return !!value && value.startsWith("/") && !value.startsWith("//");
}

export function saveOnboardingResume(consents: ConsentRequest): void {
  try {
    sessionStorage.setItem(CONSENTS_KEY, JSON.stringify(consents));
    sessionStorage.setItem(RETURN_TO_KEY, DEFAULT_RETURN_TO);
  } catch {
    // 저장소를 사용할 수 없는 환경에서는 로그인 자체를 막지 않는다.
  }
}

export function readPendingOnboardingConsents(): ConsentRequest | null {
  try {
    const raw = sessionStorage.getItem(CONSENTS_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<ConsentRequest>;
    if (typeof parsed.termsAgreed !== "boolean" || typeof parsed.privacyAgreed !== "boolean") return null;
    return {
      termsAgreed: parsed.termsAgreed,
      privacyAgreed: parsed.privacyAgreed,
      aiDataAgreed: parsed.aiDataAgreed === true,
      resumeAnalysisAgreed: parsed.resumeAnalysisAgreed === true,
      marketingAgreed: parsed.marketingAgreed === true,
    };
  } catch {
    return null;
  }
}

export function onboardingReturnTo(): string {
  try {
    const value = sessionStorage.getItem(RETURN_TO_KEY);
    return isSafeInternalPath(value) ? value : DEFAULT_RETURN_TO;
  } catch {
    return DEFAULT_RETURN_TO;
  }
}

export function clearOnboardingResume(): void {
  try {
    sessionStorage.removeItem(CONSENTS_KEY);
    sessionStorage.removeItem(RETURN_TO_KEY);
  } catch {
    // no-op
  }
}
