import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api } from "../lib/api";
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "../lib/tokenStore";

export interface MeUser {
  id: number;
  email: string;
  name: string;
  role: string;
  userType: string;
  emailVerified: boolean;
  plan: string;
  credit: number;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: MeUser;
}

export type SocialProvider = "google" | "kakao" | "naver";

interface AuthContextValue {
  user: MeUser | null;
  loading: boolean;
  isAuthenticated: boolean;
  login(email: string, password: string): Promise<void>;
  register(email: string, password: string, name: string): Promise<void>;
  socialLogin(provider: SocialProvider): void;
  logout(): Promise<void>;
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

  const login = useCallback(async (email: string, password: string) => {
    const res = await api<TokenResponse>(
      "/auth/login",
      { method: "POST", body: JSON.stringify({ email, password }) },
      { auth: false },
    );
    setTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
    setUser(res.user);
  }, []);

  const register = useCallback(async (email: string, password: string, name: string) => {
    const res = await api<TokenResponse>(
      "/auth/register",
      { method: "POST", body: JSON.stringify({ email, password, name }) },
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
