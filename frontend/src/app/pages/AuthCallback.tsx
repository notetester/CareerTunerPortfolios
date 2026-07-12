import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { getMyConsents, saveMyConsents } from "../auth/consentApi";
import { useAuth } from "../auth/AuthContext";
import { setTokens } from "../lib/tokenStore";
import { isNativeApp } from "@/platform/capacitor";
import { exchangePendingNativeOAuth } from "@/platform/nativeOAuth";
import {
  clearOnboardingResume,
  onboardingReturnTo,
  readPendingOnboardingConsents,
} from "@/features/onboarding/onboardingSession";

/**
 * 소셜 로그인 콜백 화면.
 *
 * 웹은 기존 HTTPS 콜백 hash를, 네이티브는 일회성 handoffCode를 처리한다.
 * 네이티브 access/refresh token은 URL을 거치지 않고 교환 응답으로만 받는다.
 */
export function AuthCallbackPage() {
  const navigate = useNavigate();
  const { completeLogin, refreshMe } = useAuth();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const handleCallback = async () => {
      try {
        if (isNativeApp()) {
          const params = new URLSearchParams(window.location.search);
          if (params.get("error")) {
            throw new Error("소셜 로그인이 취소되었거나 제공자 인증에 실패했습니다.");
          }
          const handoffCode = params.get("handoffCode");
          if (!handoffCode) throw new Error("로그인 인계 코드를 받지 못했습니다.");
          const token = await exchangePendingNativeOAuth(handoffCode);
          completeLogin(token);
        } else {
          const hash = window.location.hash.startsWith("#") ? window.location.hash.slice(1) : "";
          const params = new URLSearchParams(hash);
          const query = new URLSearchParams(window.location.search);
          if (query.get("error") || params.get("error")) {
            throw new Error("소셜 로그인에 실패했습니다. 다시 시도해 주세요.");
          }
          const accessToken = params.get("accessToken");
          const refreshToken = params.get("refreshToken");
          if (!accessToken || !refreshToken) throw new Error("로그인 정보를 받지 못했습니다.");
          setTokens({ accessToken, refreshToken });
          await refreshMe();
        }

        const consent = await getMyConsents();
        const pending = readPendingOnboardingConsents();
        if (consent.requiredConsentsMissing) {
          navigate("/auth/social-consent", { replace: true });
          return;
        }
        if (pending) {
          await saveMyConsents(pending);
          const returnTo = onboardingReturnTo();
          clearOnboardingResume();
          navigate(returnTo, { replace: true });
          return;
        }
        navigate("/dashboard", { replace: true });
      } catch (err) {
        setError(err instanceof Error ? err.message : "소셜 로그인 후처리에 실패했습니다.");
      }
    };

    void handleCallback();
  }, [completeLogin, navigate, refreshMe]);

  return (
    <div className="flex min-h-[calc(100vh-120px)] items-center justify-center px-4">
      <div className="space-y-3 text-center">
        {error ? (
          <>
            <p className="font-semibold text-slate-800">{error}</p>
            <button className="text-sm text-blue-600 hover:underline" onClick={() => navigate("/login")}>
              로그인으로 돌아가기
            </button>
          </>
        ) : (
          <p className="text-slate-500">로그인 처리 중...</p>
        )}
      </div>
    </div>
  );
}
