import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { setTokens } from "../lib/tokenStore";

/**
 * 소셜 로그인 콜백 착지점. 백엔드가
 * /auth/callback#accessToken=…&refreshToken=…  (또는 #error=…) 로 리다이렉트한다.
 */
export function AuthCallbackPage() {
  const navigate = useNavigate();
  const { refreshMe } = useAuth();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const hash = window.location.hash.startsWith("#") ? window.location.hash.slice(1) : "";
    const params = new URLSearchParams(hash);

    if (params.get("error")) {
      setError("소셜 로그인에 실패했습니다. 다시 시도해 주세요.");
      return;
    }
    const accessToken = params.get("accessToken");
    const refreshToken = params.get("refreshToken");
    if (accessToken && refreshToken) {
      setTokens({ accessToken, refreshToken });
      refreshMe().then(() => navigate("/dashboard", { replace: true }));
    } else {
      setError("로그인 정보를 받지 못했습니다.");
    }
  }, [navigate, refreshMe]);

  return (
    <div className="min-h-[calc(100vh-120px)] flex items-center justify-center px-4">
      <div className="text-center space-y-3">
        {error ? (
          <>
            <p className="text-slate-800 font-semibold">{error}</p>
            <button className="text-blue-600 hover:underline text-sm" onClick={() => navigate("/login")}>
              로그인으로 돌아가기
            </button>
          </>
        ) : (
          <p className="text-slate-500">로그인 처리 중…</p>
        )}
      </div>
    </div>
  );
}
