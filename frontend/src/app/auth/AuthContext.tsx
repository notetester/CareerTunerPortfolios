import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api } from "../lib/api";
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "../lib/tokenStore";

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

export type SocialProvider = "google" | "kakao" | "naver";

export interface RegisterConsents {
  termsAgreed: boolean;
  privacyAgreed: boolean;
  aiDataAgreed?: boolean;
  marketingAgreed?: boolean;
}

interface AuthContextValue {
  user: MeUser | null;
  loading: boolean;
  isAuthenticated: boolean;
  login(identifier: string, password: string): Promise<void>;
  register(loginId: string, email: string | null, password: string, name: string, consents: RegisterConsents): Promise<void>;
  socialLogin(provider: SocialProvider): void;
  logout(): Promise<void>;
  logoutAll(): Promise<void>;
  refreshMe(): Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeUser | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshMe = useCallback(async () => {
    if (!getAccessToken()) {
      setUser(null);
      return;
    }
    try {
      const me = await api<MeUser>("/auth/me", { method: "GET" });
      setUser(me);
    } catch {
      clearTokens();
      setUser(null);
    }
  }, []);

  // 새로고침 시 저장된 토큰으로 세션 복원
  useEffect(() => {
    refreshMe().finally(() => setLoading(false));
  }, [refreshMe]);

  const login = useCallback(async (identifier: string, password: string) => {
    const res = await api<TokenResponse>(
      "/auth/login",
      // 백엔드 호환성을 위해 필드명은 email을 유지하되, 값은 로그인 아이디 또는 이메일을 허용한다.
      { method: "POST", body: JSON.stringify({ email: identifier, password }) },
      { auth: false },
    );
    setTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
    setUser(res.user);
  }, []);

  const register = useCallback(async (loginId: string, email: string | null, password: string, name: string, consents: RegisterConsents) => {
    const res = await api<TokenResponse>(
      "/auth/register",
      { method: "POST", body: JSON.stringify({ loginId, email, password, name, ...consents }) },
      { auth: false },
    );
    setTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
    setUser(res.user);
  }, []);

  const socialLogin = useCallback((provider: SocialProvider) => {
    // 전체 페이지 이동 → 백엔드가 제공자로 리다이렉트
    window.location.href = `/api/auth/oauth/${provider}`;
  }, []);

  const logout = useCallback(async () => {
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
