import { useEffect, useState } from "react";
import { getOAuthProviderAvailability, type OAuthProviderAvailability } from "./authApi";

const NONE_AVAILABLE: OAuthProviderAvailability = {
  google: false,
  kakao: false,
  naver: false,
};

/**
 * 소셜 로그인 버튼이 설정되지 않은 제공자로 이동하지 않도록 가용성을 fail-closed로 조회한다.
 */
export function useOAuthProviderAvailability() {
  const [providers, setProviders] = useState<OAuthProviderAvailability>(NONE_AVAILABLE);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    void getOAuthProviderAvailability()
      .then((next) => {
        if (cancelled) return;
        setProviders(next);
      })
      .catch(() => {
        if (cancelled) return;
        setProviders(NONE_AVAILABLE);
        setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return { providers, loading, error };
}
