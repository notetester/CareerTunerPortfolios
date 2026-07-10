import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useAuth } from "./AuthContext";
import {
  getMyConsents,
  revokeConsent as requestRevokeConsent,
  saveMyConsents,
  type ConsentRequest,
  type ConsentStatus,
  type ConsentType,
} from "./consentApi";

interface ConsentContextValue {
  status: ConsentStatus | null;
  loading: boolean;
  error: string | null;
  refresh(): Promise<void>;
  save(request: ConsentRequest): Promise<ConsentStatus>;
  revoke(consentType: ConsentType): Promise<ConsentStatus>;
}

const ConsentContext = createContext<ConsentContextValue | null>(null);

export function ConsentProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated, loading: authLoading } = useAuth();
  const [status, setStatus] = useState<ConsentStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!isAuthenticated) {
      setStatus(null);
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setStatus(await getMyConsents());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "동의 상태를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (!authLoading) void refresh();
  }, [authLoading, refresh]);

  const save = useCallback(async (request: ConsentRequest) => {
    const next = await saveMyConsents(request);
    setStatus(next);
    setError(null);
    return next;
  }, []);

  const revoke = useCallback(async (consentType: ConsentType) => {
    const next = await requestRevokeConsent(consentType);
    setStatus(next);
    setError(null);
    return next;
  }, []);

  const value = useMemo<ConsentContextValue>(() => ({ status, loading, error, refresh, save, revoke }), [status, loading, error, refresh, save, revoke]);
  return <ConsentContext.Provider value={value}>{children}</ConsentContext.Provider>;
}

export function useConsent(): ConsentContextValue {
  const value = useContext(ConsentContext);
  if (!value) throw new Error("useConsent must be used within <ConsentProvider>");
  return value;
}
