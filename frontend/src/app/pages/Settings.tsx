import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Database, Lock, RefreshCw, Save, Shield, UserCog } from "lucide-react";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Checkbox } from "../components/ui/checkbox";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { findConsentTerm, type ConsentTerm } from "../auth/consentTerms";
import { getMyConsents, revokeAiConsent, saveMyConsents, type ConsentStatus } from "../auth/consentApi";
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
  const navigate = useNavigate();

  const [consent, setConsent] = useState<ConsentStatus | null>(null);
  const [terms, setTerms] = useState(true);
  const [privacy, setPrivacy] = useState(true);
  const [aiData, setAiData] = useState(false);
  const [marketing, setMarketing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const next = await getMyConsents();
      applyConsent(next);
    } catch (err) {
      setError(err instanceof Error ? err.message : "동의 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const applyConsent = (next: ConsentStatus) => {
    setConsent(next);
    setTerms(next.termsAgreed);
    setPrivacy(next.privacyAgreed);
    setAiData(next.aiDataAgreed);
    setMarketing(next.marketingAgreed);
  };

  const saveConsent = async () => {
    setSaving(true);
    setMessage(null);
    setError(null);
    try {
      const next = await saveMyConsents({
        termsAgreed: terms,
        privacyAgreed: privacy,
        aiDataAgreed: aiData,
        marketingAgreed: marketing,
      });
      applyConsent(next);
      setMessage("동의 설정이 저장되었습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "동의 설정 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const revokeAi = async () => {
    setSaving(true);
    setMessage(null);
    setError(null);
    try {
      const next = await revokeAiConsent();
      applyConsent(next);
      setMessage("AI 데이터 사용 동의를 철회했습니다. 이후 AI 프로필 분석 기능은 제한됩니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 데이터 동의 철회에 실패했습니다.");
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
          <Button variant="outline" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
            새로고침
          </Button>
        </div>

        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
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
                <ConsentToggle term={findConsentTerm("TERMS")} checked={terms} onChange={setTerms} />
                <ConsentToggle term={findConsentTerm("PRIVACY")} checked={privacy} onChange={setPrivacy} />
                <ConsentToggle term={findConsentTerm("MARKETING")} checked={marketing} onChange={setMarketing} />
                <Button onClick={() => void saveConsent()} disabled={saving} className="bg-blue-600 text-white hover:bg-blue-700">
                  <Save className="size-4" />
                  저장
                </Button>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="ai-consent" className="mt-5">
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
                  동의가 꺼져 있으면 프로필 저장은 가능하지만 AI 요약, 기술 추출, 완성도 진단 API는 실행되지 않습니다.
                  철회는 삭제가 아니라 감사 가능한 이력으로 남기는 방식입니다.
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button onClick={() => void saveConsent()} disabled={saving} className="bg-blue-600 text-white hover:bg-blue-700">
                    <Save className="size-4" />
                    현재 설정 저장
                  </Button>
                  <Button variant="outline" onClick={() => void revokeAi()} disabled={saving || !consent?.aiDataAgreed}>
                    AI 동의 철회
                  </Button>
                </div>
                <div className="text-xs text-slate-500">
                  현재 상태: {consent?.aiDataAgreed ? <Badge className="bg-blue-100 text-blue-700">동의</Badge> : <Badge className="bg-slate-200 text-slate-700">미동의/철회</Badge>}
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="notifications" className="mt-5">
            <NotificationSettings />
          </TabsContent>

          <TabsContent value="blocks" className="mt-5">
            <PrivacySettings />
          </TabsContent>
        </Tabs>
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
            {term.required && <Badge className="bg-blue-100 text-blue-700">필수</Badge>}
            <span className="text-xs text-slate-400">시행일 {term.effectiveDate}</span>
          </div>
        </div>
        <Checkbox checked={checked} onCheckedChange={(value) => onChange(value === true)} />
      </label>
      <button type="button" className="mt-3 text-xs font-semibold text-blue-600 hover:underline" onClick={() => setOpen((value) => !value)}>
        {open ? "약관 접기" : "약관 전문 보기"}
      </button>
      {open && (
        <ul className="mt-2 space-y-1 rounded-md bg-slate-50 p-3 text-xs leading-5 text-slate-600">
          {term.body.map((line) => <li key={line}>- {line}</li>)}
        </ul>
      )}
    </div>
  );
}
