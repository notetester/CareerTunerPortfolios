import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
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
  const { user, isAuthenticated, loading: authLoading } = useAuth();
  const [status, setStatus] = useState<ConsentStatus | null>(null);
  const [statusAccountId, setStatusAccountId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestGeneration = useRef(0);
  const currentAccountIdRef = useRef<number | null>(user?.id ?? null);
  currentAccountIdRef.current = user?.id ?? null;

  const refresh = useCallback(async () => {
    const accountId = user?.id ?? null;
    const generation = ++requestGeneration.current;
    if (!isAuthenticated || accountId == null) {
      setStatus(null);
      setStatusAccountId(null);
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const next = await getMyConsents();
      if (generation !== requestGeneration.current || currentAccountIdRef.current !== accountId) return;
      setStatus(next);
      setStatusAccountId(accountId);
    } catch (requestError) {
      if (generation !== requestGeneration.current || currentAccountIdRef.current !== accountId) return;
      setError(requestError instanceof Error ? requestError.message : "동의 상태를 불러오지 못했습니다.");
    } finally {
      if (generation === requestGeneration.current && currentAccountIdRef.current === accountId) {
        setLoading(false);
      }
    }
  }, [isAuthenticated, user?.id]);

  useEffect(() => {
    if (!authLoading) void refresh();
    return () => {
      requestGeneration.current += 1;
    };
  }, [authLoading, refresh]);

  const save = useCallback(async (request: ConsentRequest) => {
    const accountId = currentAccountIdRef.current;
    const next = await saveMyConsents(request);
    if (accountId == null || currentAccountIdRef.current !== accountId) return next;
    setStatus(next);
    setStatusAccountId(accountId);
    setError(null);
    return next;
  }, []);

  const revoke = useCallback(async (consentType: ConsentType) => {
    const accountId = currentAccountIdRef.current;
    const next = await requestRevokeConsent(consentType);
    if (accountId == null || currentAccountIdRef.current !== accountId) return next;
    setStatus(next);
    setStatusAccountId(accountId);
    setError(null);
    return next;
  }, []);

  const accountId = user?.id ?? null;
  const visibleStatus = statusAccountId === accountId ? status : null;
  const visibleLoading = authLoading || loading || (isAuthenticated && visibleStatus == null && error == null);
  const value = useMemo<ConsentContextValue>(
    () => ({ status: visibleStatus, loading: visibleLoading, error, refresh, save, revoke }),
    [visibleStatus, visibleLoading, error, refresh, save, revoke],
  );
  return <ConsentContext.Provider value={value}>{children}</ConsentContext.Provider>;
}

export function useConsent(): ConsentContextValue {
  const value = useContext(ConsentContext);
  if (!value) throw new Error("useConsent must be used within <ConsentProvider>");
  return value;
}
