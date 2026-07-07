import { useCallback, useEffect, useState } from "react";
import { AtSign, KeyRound, Link2, MailCheck, Phone, ShieldCheck } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { requestPasswordReset } from "@/app/auth/authApi";
import { getAccountInfo, requestEmailRegistration, setLoginId, setPhone, startSocialLink, unlinkSocialProvider } from "../api/nicknameProfileApi";
import { PROVIDER_LABELS, type AccountInfo } from "../types/nicknameProfile";

/**
 * 계정 정보 카드 — 로그인 아이디(최초 1회 설정), 전화번호, 연결된 소셜 계정.
 *
 * 아이디는 설정 후 변경 불가, 전화번호는 언제든 변경 가능(인증은 선택적·스텁).
 */
export function AccountInfoCard() {
  const [info, setInfo] = useState<AccountInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [loginIdDraft, setLoginIdDraft] = useState("");
  const [emailDraft, setEmailDraft] = useState("");
  const [phoneDraft, setPhoneDraft] = useState("");
  const [savingLoginId, setSavingLoginId] = useState(false);
  const [sendingEmail, setSendingEmail] = useState(false);
  const [sendingPassword, setSendingPassword] = useState(false);
  const [socialBusy, setSocialBusy] = useState<string | null>(null);
  const [savingPhone, setSavingPhone] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const next = await getAccountInfo();
      setInfo(next);
      setEmailDraft(next.temporaryEmail ? "" : next.email ?? "");
      setPhoneDraft(next.phone ?? "");
    } catch (e) {
      setError(e instanceof Error ? e.message : "계정 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submitLoginId = async () => {
    const value = loginIdDraft.trim().toLowerCase();
    if (!value) {
      setError("아이디를 입력해 주세요.");
      return;
    }
    setSavingLoginId(true);
    setError(null);
    setMessage(null);
    try {
      setInfo(await setLoginId(value));
      setLoginIdDraft("");
      setMessage("로그인 아이디를 설정했습니다.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "아이디 설정에 실패했습니다.");
    } finally {
      setSavingLoginId(false);
    }
  };

  const submitEmailRegistration = async () => {
    const value = emailDraft.trim().toLowerCase();
    if (!/.+@.+\..+/.test(value)) {
      setError("인증받을 이메일을 올바르게 입력해 주세요.");
      return;
    }
    setSendingEmail(true);
    setError(null);
    setMessage(null);
    try {
      await requestEmailRegistration(value);
      setMessage("인증 메일을 보냈습니다. 메일의 링크를 열면 로그인 이메일로 연결됩니다.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "이메일 인증 요청에 실패했습니다.");
    } finally {
      setSendingEmail(false);
    }
  };

  const submitPasswordSetup = async () => {
    if (!info || !info.email || info.temporaryEmail || !info.emailVerified) {
      setError("먼저 실제 이메일 등록과 인증을 완료해 주세요.");
      return;
    }
    setSendingPassword(true);
    setError(null);
    setMessage(null);
    try {
      await requestPasswordReset(info.email);
      setMessage("비밀번호 설정 메일을 보냈습니다. 메일의 링크에서 새 비밀번호를 설정해 주세요.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "비밀번호 설정 메일 발송에 실패했습니다.");
    } finally {
      setSendingPassword(false);
    }
  };

  const connectSocial = async (provider: string) => {
    setSocialBusy(provider);
    setError(null);
    setMessage(null);
    try {
      const { authorizationUrl } = await startSocialLink(provider);
      window.location.href = authorizationUrl;
    } catch (e) {
      setError(e instanceof Error ? e.message : "소셜 계정 연결을 시작하지 못했습니다.");
      setSocialBusy(null);
    }
  };

  const disconnectSocial = async (provider: string) => {
    if (!window.confirm(`${PROVIDER_LABELS[provider] ?? provider} 계정 연결을 해제할까요?`)) {
      return;
    }
    setSocialBusy(provider);
    setError(null);
    setMessage(null);
    try {
      setInfo(await unlinkSocialProvider(provider));
      setMessage("소셜 계정 연결을 해제했습니다.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "소셜 계정 연결 해제에 실패했습니다.");
    } finally {
      setSocialBusy(null);
    }
  };

  const submitPhone = async () => {
    const value = phoneDraft.trim();
    if (!value) {
      setError("전화번호를 입력해 주세요.");
      return;
    }
    setSavingPhone(true);
    setError(null);
    setMessage(null);
    try {
      setInfo(await setPhone(value));
      setMessage("전화번호를 저장했습니다.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "전화번호 저장에 실패했습니다.");
    } finally {
      setSavingPhone(false);
    }
  };

  if (loading) {
    return (
      <Card className="border-slate-200">
        <CardContent className="py-8 text-center text-sm text-slate-500">계정 정보를 불러오는 중...</CardContent>
      </Card>
    );
  }

  return (
    <Card className="border-slate-200">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <ShieldCheck className="size-5 text-blue-600" />
          계정 정보
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        {message && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}

        <div className="grid gap-1">
          <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <AtSign className="size-3.5" />
            이메일
          </span>
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm text-slate-800">
              {info?.temporaryEmail ? "실제 이메일 미등록" : info?.email}
            </span>
            {info?.temporaryEmail ? (
              <Badge className="bg-amber-100 text-amber-700">등록 필요</Badge>
            ) : (
              <Badge className={info?.emailVerified ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"}>
                {info?.emailVerified ? "인증 완료" : "인증 필요"}
              </Badge>
            )}
          </div>
          {info?.temporaryEmail && (
            <p className="text-xs text-slate-500">
              소셜 로그인에서 이메일을 받지 못해 임시 이메일이 저장된 상태입니다. 실제 이메일을 등록해야 이메일 로그인과 비밀번호 설정을 사용할 수 있습니다.
            </p>
          )}
        </div>

        {info?.emailRegistrationRequired && (
          <div className="space-y-2 rounded-xl border border-blue-100 bg-blue-50/60 p-3">
            <span className="flex items-center gap-1.5 text-xs font-bold text-blue-700">
              <MailCheck className="size-3.5" />
              로그인 이메일 등록/인증
            </span>
            <div className="flex flex-wrap gap-2">
              <Input
                className="max-w-xs bg-white"
                value={emailDraft}
                onChange={(e) => setEmailDraft(e.target.value)}
                placeholder="인증받을 이메일"
                type="email"
              />
              <Button size="sm" className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => void submitEmailRegistration()} disabled={sendingEmail}>
                {sendingEmail ? "발송 중..." : "인증 메일 발송"}
              </Button>
            </div>
            <p className="text-xs leading-5 text-blue-700">
              링크 인증이 완료되기 전까지는 현재 계정 이메일이 바뀌지 않습니다.
            </p>
          </div>
        )}

        {info?.passwordSetupRequired && (
          <div className="space-y-2 rounded-xl border border-amber-100 bg-amber-50/70 p-3">
            <span className="flex items-center gap-1.5 text-xs font-bold text-amber-700">
              <KeyRound className="size-3.5" />
              비밀번호 로그인 설정 필요
            </span>
            <p className="text-xs leading-5 text-amber-700">
              현재 계정은 소셜 로그인 전용 상태입니다. 이메일 인증 후 비밀번호를 설정하면 아이디/이메일 로그인도 사용할 수 있습니다.
            </p>
            <Button
              size="sm"
              variant="outline"
              onClick={() => void submitPasswordSetup()}
              disabled={sendingPassword || info.temporaryEmail || !info.emailVerified}
            >
              {sendingPassword ? "발송 중..." : "비밀번호 설정 메일 받기"}
            </Button>
          </div>
        )}

        {/* 로그인 아이디 — 최초 1회 설정 */}
        <div className="space-y-2">
          <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <KeyRound className="size-3.5" />
            로그인 아이디
          </span>
          {info?.loginIdSet ? (
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-slate-800">{info.loginId}</span>
              <Badge className="bg-slate-100 text-slate-600">변경 불가</Badge>
            </div>
          ) : (
            <div className="flex flex-wrap gap-2">
              <Input
                className="max-w-xs"
                value={loginIdDraft}
                onChange={(e) => setLoginIdDraft(e.target.value)}
                placeholder="영문 소문자/숫자/밑줄 4~50자"
              />
              <Button size="sm" className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => void submitLoginId()} disabled={savingLoginId}>
                {savingLoginId ? "설정 중..." : "아이디 설정"}
              </Button>
              <p className="w-full text-xs text-amber-600">아이디는 한 번 설정하면 변경할 수 없습니다.</p>
            </div>
          )}
        </div>

        {/* 전화번호 */}
        <div className="space-y-2">
          <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <Phone className="size-3.5" />
            전화번호
          </span>
          <div className="flex flex-wrap items-center gap-2">
            <Input
              className="max-w-xs"
              value={phoneDraft}
              onChange={(e) => setPhoneDraft(e.target.value)}
              placeholder="010-1234-5678"
            />
            <Button size="sm" variant="outline" onClick={() => void submitPhone()} disabled={savingPhone}>
              {savingPhone ? "저장 중..." : "저장"}
            </Button>
            {info?.phone && (
              <Badge className={info.phoneVerified ? "bg-green-100 text-green-700" : "bg-slate-100 text-slate-600"}>
                {info.phoneVerified ? "인증됨" : "미인증"}
              </Badge>
            )}
          </div>
        </div>

        {/* 연결된 소셜 계정 */}
        <div className="space-y-2">
          <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <Link2 className="size-3.5" />
            소셜 로그인 연결
          </span>
          <div className="grid gap-2 md:grid-cols-3">
            {SOCIAL_PROVIDERS.map((provider) => {
              const linked = info?.linkedProviders.includes(provider) ?? false;
              return (
                <div key={provider} className="flex items-center justify-between gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                  <div className="min-w-0">
                    <div className="text-sm font-semibold text-slate-800">{PROVIDER_LABELS[provider] ?? provider}</div>
                    <Badge className={linked ? "bg-blue-50 text-blue-700" : "bg-slate-100 text-slate-600"}>
                      {linked ? "연결됨" : "미연결"}
                    </Badge>
                  </div>
                  {linked ? (
                    <Button size="sm" variant="outline" onClick={() => void disconnectSocial(provider)} disabled={socialBusy === provider}>
                      해제
                    </Button>
                  ) : (
                    <Button size="sm" className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => void connectSocial(provider)} disabled={socialBusy === provider}>
                      연결
                    </Button>
                  )}
                </div>
              );
            })}
          </div>
          <p className="text-xs text-slate-400">
            마지막 로그인 수단은 해제할 수 없습니다. 비밀번호 로그인이 꺼져 있다면 먼저 이메일과 비밀번호를 설정해 주세요.
          </p>
        </div>
      </CardContent>
    </Card>
  );
}

const SOCIAL_PROVIDERS = ["KAKAO", "NAVER", "GOOGLE"] as const;
