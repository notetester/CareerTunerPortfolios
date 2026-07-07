import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { getMyConsents } from "../auth/consentApi";
import { useAuth } from "../auth/AuthContext";
import { setTokens } from "../lib/tokenStore";

/**
 * 소셜 로그인 콜백 화면.
 *
 * 백엔드 OAuth 성공 핸들러가 URL hash에 accessToken/refreshToken을 담아 보내면
 * 여기서 토큰 저장 -> 내 정보 조회 -> 필수 약관 확인 순서로 후처리한다.
 */
export function AuthCallbackPage() {
  const navigate = useNavigate();
  const { refreshMe } = useAuth();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const handleCallback = async () => {
      const hash = window.location.hash.startsWith("#") ? window.location.hash.slice(1) : "";
      const params = new URLSearchParams(hash);

      if (params.get("error")) {
        setError("소셜 로그인에 실패했습니다. 다시 시도해 주세요.");
        return;
      }

      const accessToken = params.get("accessToken");
      const refreshToken = params.get("refreshToken");
      const linkedProvider = params.get("linkedProvider");
      if (!accessToken || !refreshToken) {
        setError("로그인 정보를 받지 못했습니다.");
        return;
      }

      try {
        setTokens({ accessToken, refreshToken });
        await refreshMe();

        if (linkedProvider) {
          navigate("/settings?tab=account", { replace: true });
          return;
        }

        const consent = await getMyConsents();
        navigate(consent.requiredConsentsMissing ? "/auth/social-consent" : "/dashboard", { replace: true });
      } catch (err) {
        setError(err instanceof Error ? err.message : "소셜 로그인 후처리에 실패했습니다.");
      }
    };

    void handleCallback();
  }, [navigate, refreshMe]);

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
