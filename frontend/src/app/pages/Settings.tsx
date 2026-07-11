import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { AlertTriangle, Database, History, Lock, RefreshCw, Save, Shield, UserCog } from "lucide-react";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Checkbox } from "../components/ui/checkbox";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "../components/ui/alert-dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { findConsentTerm, type ConsentTerm } from "../auth/consentTerms";
import type { ConsentStatus, ConsentType } from "../auth/consentApi";
import { useConsent } from "../auth/ConsentContext";
import { useAuth } from "../auth/AuthContext";
import { NotificationSettings } from "@/features/notification/components/NotificationSettings";
import { PrivacySettings } from "@/features/privacy/components/PrivacySettings";
import { ServerAddressSettings } from "@/features/settings/components/ServerAddressSettings";
import { AccountInfoCard } from "@/features/profile/components/AccountInfoCard";
import { MfaSettingsCard } from "@/features/profile/components/MfaSettingsCard";
import { AppLockSettings } from "../components/AppLockSettings";

const tabs = ["account", "privacy", "ai-consent", "notifications", "blocks"] as const;
type SettingsTab = (typeof tabs)[number];

export function SettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab") ?? "account";
  const activeTab: SettingsTab = tabs.includes(requestedTab as SettingsTab) ? (requestedTab as SettingsTab) : "account";
  const { logout, logoutAll } = useAuth();
  const { status: consent, loading, error: consentLoadError, refresh, save, revoke } = useConsent();
  const navigate = useNavigate();

  const [terms, setTerms] = useState(true);
  const [privacy, setPrivacy] = useState(true);
  const [aiData, setAiData] = useState(false);
  const [resumeAnalysis, setResumeAnalysis] = useState(false);
  const [marketing, setMarketing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [confirmRequiredWithdrawal, setConfirmRequiredWithdrawal] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (consent) applyConsent(consent);
  }, [consent]);

  const applyConsent = (next: ConsentStatus) => {
    setTerms(next.termsAgreed);
    setPrivacy(next.privacyAgreed);
    setAiData(next.aiDataAgreed);
    setResumeAnalysis(next.resumeAnalysisAgreed);
    setMarketing(next.marketingAgreed);
  };

  const saveConsent = async () => {
    setSaving(true);
    setMessage(null);
    setError(null);
    try {
      const next = await save({
        termsAgreed: terms,
        privacyAgreed: privacy,
        aiDataAgreed: aiData,
        resumeAnalysisAgreed: resumeAnalysis,
        marketingAgreed: marketing,
      });
      applyConsent(next);
      setMessage(next.requiredConsentsMissing
        ? "필수 동의를 철회했습니다. 다시 동의할 때까지 회원 기능 이용이 중단됩니다."
        : "동의 설정이 저장되었습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "동의 설정 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const requestSaveConsent = () => {
    const withdrawingRequired = (!!consent?.termsAgreed && !terms) || (!!consent?.privacyAgreed && !privacy);
    if (withdrawingRequired) {
      setConfirmRequiredWithdrawal(true);
      return;
    }
    void saveConsent();
  };

  const revokeSpecific = async (consentType: ConsentType) => {
    setSaving(true);
    setMessage(null);
    setError(null);
    try {
      const next = await revoke(consentType);
      applyConsent(next);
      setMessage(consentType === "AI_DATA"
        ? "AI 데이터 이용 동의를 철회했습니다. 다시 동의할 때까지 AI 기능이 중단됩니다."
        : "이력서 분석 동의를 철회했습니다. 다시 동의할 때까지 이력서 가져오기와 분석이 중단됩니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "동의 철회에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1200px] space-y-6 px-4 py-8 sm:px-6">
        <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <UserCog className="size-6 text-blue-600" />
              설정
            </h1>
            <p className="mt-1 text-sm text-slate-500">계정, 개인정보, AI 데이터 사용 동의, 알림, 차단을 관리합니다.</p>
          </div>
          <Button variant="outline" onClick={() => void refresh()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
            새로고침
          </Button>
        </div>

        {(error || consentLoadError) && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error || consentLoadError}</div>}
        {message && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}

        <Tabs value={activeTab} onValueChange={(value) => setSearchParams({ tab: value })}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-card p-1">
            <TabsTrigger value="account">계정 설정</TabsTrigger>
            <TabsTrigger value="privacy">개인정보 관리</TabsTrigger>
            <TabsTrigger value="ai-consent">AI 데이터 동의</TabsTrigger>
            <TabsTrigger value="notifications">알림 설정</TabsTrigger>
            <TabsTrigger value="blocks">차단 관리</TabsTrigger>
          </TabsList>

          <TabsContent value="account" className="mt-5 space-y-4">
            <AccountInfoCard />
            <Card className="border border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <Lock className="size-4 text-slate-600" />
                  보안
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <AppLockSettings />
                <div className="space-y-1.5 border-t border-slate-100 pt-3 text-sm text-slate-600">
                  <p>비밀번호 재설정은 로그인 화면의 비밀번호 찾기에서 이메일 인증 링크로 진행합니다.</p>
                  <p>로그인 실패 잠금과 로그인 감사 로그는 백엔드에서 자동 기록됩니다.</p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Button
                    variant="outline"
                    className="text-red-600 hover:bg-red-50"
                    onClick={async () => { await logout(); navigate("/"); }}
                  >
                    로그아웃
                  </Button>
                  <Button
                    variant="outline"
                    className="text-red-600 hover:bg-red-50"
                    onClick={async () => { await logoutAll(); navigate("/"); }}
                  >
                    모든 기기에서 로그아웃
                  </Button>
                </div>
              </CardContent>
            </Card>
            {/* 앱/개발 전용 — 배포 웹에서는 컴포넌트가 스스로 숨는다(null 렌더). */}
            <MfaSettingsCard />
            <ServerAddressSettings />
          </TabsContent>

          <TabsContent value="privacy" className="mt-5 space-y-4">
            <Card className="border border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <Shield className="size-4 text-green-600" />
                  개인정보 처리 동의
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
                  이용약관이나 개인정보 처리 동의를 철회하면 설정·법적 문서·고객센터를 제외한 회원 기능이 즉시 중단됩니다.
                  다시 동의하면 별도의 승인 대기 없이 이용할 수 있습니다.
                </div>
                <ConsentToggle term={findConsentTerm("TERMS")} checked={terms} onChange={setTerms} />
                <ConsentToggle term={findConsentTerm("PRIVACY")} checked={privacy} onChange={setPrivacy} />
                <ConsentToggle term={findConsentTerm("RESUME_ANALYSIS")} checked={resumeAnalysis} onChange={setResumeAnalysis} />
                <ConsentToggle term={findConsentTerm("MARKETING")} checked={marketing} onChange={setMarketing} />
                <div className="flex flex-wrap gap-2">
                  <Button onClick={requestSaveConsent} disabled={saving} className="bg-blue-600 text-white hover:bg-blue-700">
                    <Save className="size-4" />동의 변경 저장
                  </Button>
                  <Button variant="outline" onClick={() => void revokeSpecific("RESUME_ANALYSIS")} disabled={saving || !consent?.resumeAnalysisAgreed}>
                    이력서 분석 동의 철회
                  </Button>
                </div>
              </CardContent>
            </Card>
            <ConsentHistory history={consent?.history ?? []} filter={["TERMS", "PRIVACY", "RESUME_ANALYSIS", "MARKETING"]} />
          </TabsContent>

          <TabsContent value="ai-consent" className="mt-5 space-y-4">
            <Card className="border border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <Database className="size-4 text-purple-600" />
                  AI 데이터 사용 동의
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <ConsentToggle term={findConsentTerm("AI_DATA")} checked={aiData} onChange={setAiData} />
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-600">
                  동의가 꺼져 있으면 AI 분석, 면접 평가, 첨삭과 자동 준비 기능이 실행되지 않습니다.
                  이력서 기반 프로필 AI 도구는 별도의 이력서 분석 동의도 함께 필요합니다. 철회는 감사 가능한 변경 이력으로 남습니다.
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button onClick={requestSaveConsent} disabled={saving} className="bg-blue-600 text-white hover:bg-blue-700">
                    <Save className="size-4" />
                    현재 설정 저장
                  </Button>
                  <Button variant="outline" onClick={() => void revokeSpecific("AI_DATA")} disabled={saving || !consent?.aiDataAgreed}>
                    AI 동의 철회
                  </Button>
                </div>
                <div className="text-xs text-slate-500">
                  현재 상태: {consent?.aiDataAgreed ? <Badge className="bg-blue-100 text-blue-700">동의</Badge> : <Badge className="bg-slate-200 text-slate-700">미동의/철회</Badge>}
                </div>
              </CardContent>
            </Card>
            <ConsentHistory history={consent?.history ?? []} filter={["AI_DATA"]} />
          </TabsContent>

          <TabsContent value="notifications" className="mt-5">
            <NotificationSettings />
          </TabsContent>

          <TabsContent value="blocks" className="mt-5">
            <PrivacySettings />
          </TabsContent>
        </Tabs>

        <AlertDialog open={confirmRequiredWithdrawal} onOpenChange={setConfirmRequiredWithdrawal}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle className="flex items-center gap-2"><AlertTriangle className="size-5 text-amber-700" />필수 동의를 철회할까요?</AlertDialogTitle>
              <AlertDialogDescription className="leading-6">
                철회 즉시 현재 설정 화면, 법적 문서와 고객센터를 제외한 회원 기능을 이용할 수 없습니다.
                계정이 삭제되지는 않으며, 설정에서 다시 동의하면 이용이 재개됩니다.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel disabled={saving}>취소</AlertDialogCancel>
              <AlertDialogAction
                className="bg-red-600 text-white hover:bg-red-700"
                disabled={saving}
                onClick={() => void saveConsent()}
              >
                철회하고 제한 적용
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>
    </div>
  );
}

function ConsentToggle({
  term,
  checked,
  onChange,
}: {
  term: ConsentTerm;
  checked: boolean;
  onChange(next: boolean): void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <div className="rounded-xl border border-slate-200 p-4">
      <label className="flex items-center justify-between gap-3">
        <div>
          <div className="text-sm font-semibold text-slate-800">
            {term.title} <span className="text-xs font-normal text-slate-400">{term.version}</span>
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            {term.required
              ? <Badge className="bg-blue-100 text-blue-700">필수</Badge>
              : <Badge variant="outline">선택</Badge>}
            <span className="text-xs text-slate-400">시행일 {term.effectiveDate}</span>
          </div>
        </div>
        <Checkbox checked={checked} onCheckedChange={(value) => onChange(value === true)} />
      </label>
      <button type="button" className="mt-3 text-xs font-semibold text-blue-600 hover:underline" onClick={() => setOpen((value) => !value)}>
        {open ? "요약 접기" : "요약 보기"}
      </button>
      <Link className="ml-3 text-xs font-semibold text-slate-500 hover:text-blue-700 hover:underline" to={term.documentHref}>전문 열기</Link>
      <p className="mt-3 text-xs leading-5 text-amber-800">{term.restriction}</p>
      {open && (
        <ul className="mt-2 space-y-1 rounded-md bg-slate-50 p-3 text-xs leading-5 text-slate-600">
          {term.body.map((line) => <li key={line}>- {line}</li>)}
        </ul>
      )}
    </div>
  );
}

function ConsentHistory({ history, filter }: { history: ConsentStatus["history"]; filter: ConsentType[] }) {
  const rows = history.filter((row) => filter.includes(row.consentType as ConsentType)).slice(0, 8);
  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base"><History className="size-4 text-slate-600" />최근 동의 이력</CardTitle>
      </CardHeader>
      <CardContent>
        {rows.length === 0 ? (
          <p className="text-sm text-slate-500">아직 기록된 동의 변경이 없습니다.</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {rows.map((row) => {
              const term = findConsentTerm(row.consentType as ConsentTerm["code"]);
              const timestamp = row.agreed ? row.agreedAt : row.revokedAt;
              return (
                <div key={row.id} className="flex flex-col gap-2 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <div className="font-semibold text-slate-800">{term.title}</div>
                    <div className="mt-1 text-xs text-slate-400">
                      {row.consentVersion || term.version} · {timestamp ? new Date(timestamp).toLocaleString("ko-KR") : "시각 정보 없음"} · {row.source ?? "USER"}
                    </div>
                  </div>
                  <Badge className={row.agreed && !row.revokedAt ? "bg-green-100 text-green-700" : "bg-slate-200 text-slate-700"}>
                    {row.agreed && !row.revokedAt ? "동의" : "철회"}
                  </Badge>
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
