import type { ComponentType, ReactNode } from "react";
import { Link, Navigate, useLocation } from "react-router";
import { AlertTriangle, FileText, RefreshCw, ShieldCheck, ShieldOff } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { useAuth } from "./AuthContext";
import { useConsent } from "./ConsentContext";
import type { ConsentType } from "./consentApi";

const consentLabels: Record<ConsentType, { label: string; legalHref: string }> = {
  TERMS: { label: "서비스 이용약관", legalHref: "/legal/terms" },
  PRIVACY: { label: "개인정보 처리방침", legalHref: "/legal/privacy" },
  AI_DATA: { label: "AI 데이터 이용 동의", legalHref: "/legal/ai-data-consent" },
  RESUME_ANALYSIS: { label: "이력서 분석 개인정보 수집·이용 동의", legalHref: "/legal/resume-analysis-consent" },
  MARKETING: { label: "마케팅 수신 동의", legalHref: "/legal/marketing" },
};

function hasConsent(type: ConsentType, status: NonNullable<ReturnType<typeof useConsent>["status"]>): boolean {
  switch (type) {
    case "TERMS": return status.termsAgreed;
    case "PRIVACY": return status.privacyAgreed;
    case "AI_DATA": return status.aiDataAgreed;
    case "RESUME_ANALYSIS": return status.resumeAnalysisAgreed;
    case "MARKETING": return status.marketingAgreed;
  }
}

function isRecoveryPath(pathname: string): boolean {
  return pathname === "/"
    || pathname === "/login"
    || pathname.startsWith("/auth/")
    || pathname === "/settings"
    || pathname.startsWith("/settings/")
    || pathname.startsWith("/legal/")
    || pathname === "/features"
    || pathname.startsWith("/service/")
    || pathname === "/support"
    || pathname.startsWith("/support/")
    || pathname.startsWith("/company/about")
    || pathname.startsWith("/company/team")
    || pathname.startsWith("/company/careers")
    || pathname.startsWith("/company/blog")
    || pathname.startsWith("/company/press")
    || pathname.startsWith("/company/social");
}

export function RequiredConsentBoundary({ children }: { children: ReactNode }) {
  const { isAuthenticated, loading: authLoading } = useAuth();
  const { status, loading, error, refresh } = useConsent();
  const location = useLocation();

  // 루트는 비로그인 랜딩이면서 네이티브 로그인 홈이기도 하다. 로그인 홈까지
  // 공개 복구 경로로 취급하면 필수 동의를 철회한 사용자가 AppHome을 계속 이용하게 된다.
  if (isRecoveryPath(location.pathname) && location.pathname !== "/") return children;
  if (authLoading && !isAuthenticated) return <ConsentLoading />;
  if (!isAuthenticated) return children;
  if (loading && !status) return <ConsentLoading />;
  if (error && !status) return <ConsentLoadFailure error={error} onRetry={refresh} />;
  if (!status) return <ConsentLoading />;
  if (!status.requiredConsentsMissing) return children;

  const missing: ConsentType[] = [];
  if (!status.termsAgreed) missing.push("TERMS");
  if (!status.privacyAgreed) missing.push("PRIVACY");
  return <ConsentBlocked requirements={missing} requiredServiceConsent />;
}

/** 회원 전용 화면을 API 401 오류 화면으로 먼저 마운트하지 않고 로그인으로 복귀시킨다. */
export function AuthenticatedRouteBoundary({ children }: { children: ReactNode }) {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  // 재검증(loading) 중이라도 이미 인증된 세션이면 자식을 유지한다. 결제 성공 등에서 refreshMe()
  // 재검증마다 페이지가 언마운트→재마운트되며 상태가 초기화(무한 깜빡임)되던 회귀 방지(ConsentGate 와 동일 패턴).
  if (loading && !isAuthenticated) return <ConsentLoading />;
  if (!isAuthenticated) {
    const returnTo = `${location.pathname}${location.search}`;
    return <Navigate to={`/login?returnTo=${encodeURIComponent(returnTo)}`} replace />;
  }
  return children;
}

export function withAuthGate<P extends object>(Component: ComponentType<P>) {
  function AuthenticatedPage(props: P) {
    return <AuthenticatedRouteBoundary><Component {...props} /></AuthenticatedRouteBoundary>;
  }
  AuthenticatedPage.displayName = `Authenticated(${Component.displayName ?? Component.name ?? "Page"})`;
  return AuthenticatedPage;
}

export function ConsentGate({ requirements, children }: { requirements: ConsentType[]; children: ReactNode }) {
  const { isAuthenticated, loading: authLoading } = useAuth();
  const { status, loading, error, refresh } = useConsent();
  const location = useLocation();

  if (!authLoading && !isAuthenticated) {
    const returnTo = `${location.pathname}${location.search}`;
    return <Navigate to={`/login?returnTo=${encodeURIComponent(returnTo)}`} replace />;
  }
  // 재검증(authLoading) 중이라도 이미 확인된 세션(user 존재)이면 children을 유지한다.
  // AI 과금 성공 → 크레딧 이벤트 → refreshMe() 재검증 순간마다 페이지가 언마운트되어
  // 면접 세션·입력 상태가 통째로 초기화되던 회귀 방지. 동의 차단 자체는 아래에서 그대로 평가된다.
  if ((authLoading && !isAuthenticated) || (loading && !status)) return <ConsentLoading />;
  if (error && !status) return <ConsentLoadFailure error={error} onRetry={refresh} />;
  if (!status) return <ConsentLoading />;
  const missing = requirements.filter((type) => !hasConsent(type, status));
  return missing.length === 0 ? children : <ConsentBlocked requirements={missing} />;
}

export function withConsentGate<P extends object>(Component: ComponentType<P>, requirements: ConsentType[]) {
  function ConsentProtectedPage(props: P) {
    return <ConsentGate requirements={requirements}><Component {...props} /></ConsentGate>;
  }
  ConsentProtectedPage.displayName = `ConsentProtected(${Component.displayName ?? Component.name ?? "Page"})`;
  return ConsentProtectedPage;
}

function ConsentBlocked({ requirements, requiredServiceConsent = false }: { requirements: ConsentType[]; requiredServiceConsent?: boolean }) {
  const settingsTab = requirements.includes("AI_DATA") ? "ai-consent" : "privacy";
  return (
    <div className="mx-auto flex min-h-[560px] max-w-[860px] items-center px-4 py-10 sm:px-6">
      <Card className="w-full border-amber-200 bg-card">
        <CardHeader>
          <div className="flex size-11 items-center justify-center rounded-lg bg-amber-100 text-amber-800"><ShieldOff className="size-5" /></div>
          <CardTitle className="text-xl">{requiredServiceConsent ? "필수 동의를 다시 확인해 주세요" : "이 기능에 필요한 동의가 없습니다"}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-5">
          <p className="text-sm leading-6 text-slate-600">
            {requiredServiceConsent
              ? "철회한 필수 동의에 다시 동의할 때까지 회원 기능을 이용할 수 없습니다. 법적 문서와 고객센터는 계속 확인할 수 있습니다."
              : "동의를 철회한 상태에서는 관련 데이터 처리를 시작하지 않습니다. 설정에서 다시 동의하면 기능을 즉시 이용할 수 있습니다."}
          </p>
          <ul className="space-y-2">
            {requirements.map((type) => (
              <li key={type} className="flex flex-col gap-2 rounded-lg border border-slate-200 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
                <span className="inline-flex items-center gap-2 font-semibold text-slate-800"><FileText className="size-4 text-amber-700" />{consentLabels[type].label}</span>
                <Link className="text-xs font-semibold text-blue-700 hover:underline" to={consentLabels[type].legalHref}>전문 보기</Link>
              </li>
            ))}
          </ul>
          <div className="flex flex-wrap gap-2">
            <Button asChild><Link to={`/settings/${settingsTab}`}><ShieldCheck className="size-4" />설정에서 다시 동의</Link></Button>
            <Button asChild variant="outline"><Link to="/support/contact">도움 요청</Link></Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function ConsentLoading() {
  return <div className="flex min-h-[420px] items-center justify-center gap-2 text-sm text-slate-500"><RefreshCw className="size-4 animate-spin" />동의 상태를 확인하는 중입니다.</div>;
}

function ConsentLoadFailure({ error, onRetry }: { error: string; onRetry(): Promise<void> }) {
  return (
    <div className="mx-auto flex min-h-[420px] max-w-xl items-center px-4">
      <div className="w-full rounded-lg border border-red-200 bg-red-50 p-5">
        <div className="flex items-center gap-2 font-bold text-red-800"><AlertTriangle className="size-4" />동의 상태를 확인하지 못했습니다</div>
        <p className="mt-2 text-sm text-red-700">{error}</p>
        <Button variant="outline" className="mt-4" onClick={() => void onRetry()}><RefreshCw className="size-4" />다시 확인</Button>
      </div>
    </div>
  );
}
