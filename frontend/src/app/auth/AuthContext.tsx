import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { api } from "../lib/api";
import { apiBase } from "../lib/apiBase";
import { subscribeCreditBalanceChanged } from "../lib/creditBalanceEvents";
import { isOutageFallbackActive, isSocialOAuthBlocked } from "../lib/outageFallback";
import { isNativeApp } from "@/platform/capacitor";
import { cancelPendingNativeOAuth, startNativeSocialLogin } from "@/platform/nativeOAuth";
import {
  discardPendingCollaborationFiles,
  forgetPendingCollaborationFiles,
} from "../lib/pendingCollaborationFiles";
import {
  clearTokens,
  clearTokensIfUnchanged,
  getRefreshToken,
  getTokenStoreSnapshot,
  isTokenStoreSnapshotCurrent,
  setTokens,
  subscribeTokenStore,
} from "../lib/tokenStore";

export interface MeUser {
  id: number;
  email: string | null;
  name: string;
  role: string;
  userType: string;
  emailVerified: boolean;
  plan: string;
  credit: number;
  permissions?: string[];
  permissionGroups?: string[];
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: MeUser;
}

export interface LoginResponse {
  mfaRequired: boolean;
  mfaSetupRecommended: boolean;
  challengeToken: string | null;
  challengeMethod: "TOTP" | "TOTP_OR_PUSH" | string | null;
  expiresIn: number;
  token: TokenResponse | null;
}

export type SocialProvider = "google" | "kakao" | "naver";

export interface RegisterConsents {
  termsAgreed: boolean;
  privacyAgreed: boolean;
  aiDataAgreed?: boolean;
  resumeAnalysisAgreed?: boolean;
  marketingAgreed?: boolean;
}

interface AuthContextValue {
  user: MeUser | null;
  loading: boolean;
  isAuthenticated: boolean;
  login(identifier: string, password: string): Promise<LoginResponse>;
  completeLogin(token: TokenResponse): void;
  register(loginId: string, email: string | null, password: string, name: string, consents: RegisterConsents): Promise<void>;
  socialLogin(provider: SocialProvider): Promise<void>;
  logout(): Promise<void>;
  logoutAll(): Promise<void>;
  refreshMe(): Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeUser | null>(null);
  const [loading, setLoading] = useState(true);
  const refreshGeneration = useRef(0);

  const refreshMe = useCallback(async () => {
    const generation = ++refreshGeneration.current;
    const sessionSnapshot = getTokenStoreSnapshot();
    setLoading(true);
    try {
      if (!sessionSnapshot.tokens?.accessToken) {
        if (
          generation === refreshGeneration.current
          && isTokenStoreSnapshotCurrent(sessionSnapshot)
        ) {
          setUser(null);
        }
        return;
      }
      const me = await api<MeUser>("/auth/me", { method: "GET" });
      if (
        generation === refreshGeneration.current
        && isTokenStoreSnapshotCurrent(sessionSnapshot)
      ) {
        setUser(me);
      }
    } catch {
      if (
        generation === refreshGeneration.current
        && isTokenStoreSnapshotCurrent(sessionSnapshot)
      ) {
        setUser(null);
        clearTokensIfUnchanged(sessionSnapshot);
      }
    } finally {
      if (generation === refreshGeneration.current) setLoading(false);
    }
  }, []);

  // refresh가 토큰을 바꾸면 /auth/me로 role을 다시 검증한다. 검증 중에는 loading으로 관리자 경계를 닫는다.
  useEffect(() => subscribeTokenStore((event) => {
    if (event === "cleared") {
      refreshGeneration.current += 1;
      setUser(null);
      setLoading(false);
      return;
    }
    void refreshMe();
  }), [refreshMe]);

  // 새로고침 시 저장된 토큰으로 세션 복원
  useEffect(() => {
    void refreshMe();
  }, [refreshMe]);

  const completeLogin = useCallback((token: TokenResponse) => {
    // 장애 체험용 mock 토큰으로 기존 운영 세션을 덮어쓰지 않는다. 복구 reload 후 실제 토큰을 다시 사용한다.
    if (!isOutageFallbackActive()) {
      setTokens({ accessToken: token.accessToken, refreshToken: token.refreshToken });
    }
    setUser(token.user);
  }, []);

  useEffect(() => subscribeCreditBalanceChanged(({ remainingCredit }) => {
    if (remainingCredit !== undefined && Number.isSafeInteger(remainingCredit) && remainingCredit >= 0) {
      setUser((current) => (current ? { ...current, credit: remainingCredit } : current));
      return;
    }
    void refreshMe();
  }), [refreshMe]);

  const login = useCallback(async (identifier: string, password: string) => {
    const res = await api<LoginResponse>(
      "/auth/login",
      // 백엔드 호환성을 위해 필드명은 email을 유지하되, 값은 로그인 아이디 또는 이메일을 허용한다.
      { method: "POST", body: JSON.stringify({ email: identifier, password }) },
      { auth: false },
    );
    if (res.token) {
      completeLogin(res.token);
    }
    return res;
  }, [completeLogin]);

  const register = useCallback(async (
    loginId: string,
    email: string | null,
    password: string,
    name: string,
    consents: RegisterConsents,
  ) => {
    const res = await api<TokenResponse>(
      "/auth/register",
      { method: "POST", body: JSON.stringify({ loginId, email, password, name, ...consents }) },
      { auth: false },
    );
    if (!isOutageFallbackActive()) {
      setTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
    }
    setUser(res.user);
  }, []);

  const socialLogin = useCallback(async (provider: SocialProvider) => {
    if (isSocialOAuthBlocked()) return;
    if (isNativeApp()) {
      await startNativeSocialLogin(provider);
      return;
    }
    // 전체 페이지 이동 → 백엔드가 제공자로 리다이렉트
    window.location.href = `${apiBase()}/auth/oauth/${provider}`;
  }, []);

  const logout = useCallback(async () => {
    if (isNativeApp()) cancelPendingNativeOAuth();
    await discardPendingCollaborationFiles();
    forgetPendingCollaborationFiles();
    const refreshToken = getRefreshToken();
    try {
      await api<void>("/auth/logout", {
        method: "POST",
        body: JSON.stringify({ refreshToken: refreshToken ?? "" }),
      });
    } catch {
      // 서버 실패와 무관하게 로컬 세션은 종료
    }
    clearTokens();
    setUser(null);
  }, []);

  const logoutAll = useCallback(async () => {
    if (isNativeApp()) cancelPendingNativeOAuth();
    await discardPendingCollaborationFiles();
    forgetPendingCollaborationFiles();
    try {
      await api<void>("/auth/logout-all", { method: "POST" });
    } catch {
      // 서버 실패와 무관하게 로컬 세션은 종료
    }
    clearTokens();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        isAuthenticated: !!user,
        login,
        completeLogin,
        register,
        socialLogin,
        logout,
        logoutAll,
        refreshMe,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within <AuthProvider>");
  return ctx;
}
