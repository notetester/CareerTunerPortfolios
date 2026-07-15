import { useCallback, useEffect, useState } from "react";
import { Browser } from "@capacitor/browser";
import { useLocation, useNavigate } from "react-router";
import { AtSign, CheckCircle2, KeyRound, Link2, Loader2, MailCheck, Phone, ShieldCheck, Sparkles } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { requestPasswordReset } from "@/app/auth/authApi";
import type { SocialProvider } from "@/app/auth/AuthContext";
import { useOAuthProviderAvailability } from "@/app/auth/useOAuthProviderAvailability";
import { useOutageFallback } from "@/app/lib/outageFallback";
import { isNativeApp } from "@/platform/capacitor";
import { validateAuthorizationUrl, type NativeOAuthProvider } from "@/platform/nativeOAuthCore.mjs";
import { getAccountInfo, getSocialLinkUrl, requestEmailRegistration, setLoginId, unlinkSocial } from "../api/nicknameProfileApi";
import { PROVIDER_LABELS, type AccountInfo } from "../types/nicknameProfile";
import { RECAPTCHA_CONTAINER_ID, usePhoneVerification } from "../hooks/usePhoneVerification";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

/**
 * 계정 정보 카드 — 로그인 아이디(최초 1회 설정), 전화번호 인증, 연결된 소셜 계정.
 *
 * 아이디는 설정 후 변경 불가. 전화번호는 SMS/Firebase 인증으로 등록하며,
 * 인증 성공 시 백엔드가 번호 저장과 인증 상태(users.phone/phone_verified)를 함께 처리한다.
 * Settings 계정 탭과 ProfileDetail 계정 탭이 이 카드를 공유한다.
 */
export function AccountInfoCard() {
  const location = useLocation();
  const navigate = useNavigate();
  const { socialOAuthBlocked } = useOutageFallback();
  const {
    providers: oauthProviders,
    loading: oauthProvidersLoading,
    error: oauthProvidersError,
  } = useOAuthProviderAvailability();
  const [info, setInfo] = useState<AccountInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [loginIdDraft, setLoginIdDraft] = useState("");
  const [emailDraft, setEmailDraft] = useState("");
  const [savingLoginId, setSavingLoginId] = useState(false);
  const [sendingEmail, setSendingEmail] = useState(false);
  const [sendingPassword, setSendingPassword] = useState(false);
  const [socialBusy, setSocialBusy] = useState<string | null>(null);

  const load = useCallback(async (): Promise<AccountInfo | null> => {
    setLoading(true);
    setError(null);
    try {
      const next = await getAccountInfo();
      setInfo(next);
      setEmailDraft(next.temporaryEmail ? "" : next.email ?? "");
      return next;
    } catch (e) {
      setError(e instanceof Error ? e.message : "계정 정보를 불러오지 못했습니다.");
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  // 전화번호 인증 — 성공 시 백엔드가 번호 저장 + 인증 상태를 함께 갱신하므로 계정 정보를 다시 불러온다.
  const phoneVerify = usePhoneVerification({ onVerified: () => void load() });

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!isNativeApp() || USE_MOCK) return;

    let disposed = false;
    let removeListener: (() => Promise<void>) | null = null;
    void Browser.addListener("browserFinished", () => {
      setSocialBusy(null);
    }).then((handle) => {
      if (disposed) {
        void handle.remove();
      } else {
        removeListener = () => handle.remove();
      }
    }).catch(() => {
      // 브라우저 종료 이벤트가 없는 플랫폼에서도 App Link 콜백이 busy 상태를 정리한다.
    });

    return () => {
      disposed = true;
      if (removeListener) void removeListener();
    };
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const linkedValue = params.get("socialLinked")?.toUpperCase() ?? null;
    const linked = linkedValue && ["GOOGLE", "KAKAO", "NAVER"].includes(linkedValue)
      ? linkedValue
      : null;
    const linkError = params.get("socialLinkError");
    if (!linked && !linkError) return;

    let cancelled = false;
    void load().then((next) => {
      if (cancelled) return;
      setSocialBusy(null);
      if (linked && next?.linkedProviders.includes(linked)) {
        const suffix = params.get("socialMock") ? " mock 계정을 연결했습니다." : " 계정을 연결했습니다.";
        setMessage(`${PROVIDER_LABELS[linked] ?? linked}${suffix}`);
      } else if (linked) {
        setMessage(null);
        setError("소셜 계정 연결 결과를 확인하지 못했습니다. 현재 연결 상태를 확인해 주세요.");
      } else {
        setMessage(null);
        setError(linkError === "social_login_cancelled"
          ? "소셜 계정 연결을 취소했습니다."
          : "소셜 계정 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.");
      }
      void navigate("/profile/detail", { replace: true });
    });
    return () => {
      cancelled = true;
    };
  }, [load, location.search, navigate]);

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

  const linkProvider = async (provider: string) => {
    setSocialBusy(provider);
    setError(null);
    setMessage(null);
    try {
      const { url } = await getSocialLinkUrl(provider);
      if (isNativeApp()) {
        if (USE_MOCK) {
          const expectedCallback = `/profile/detail?socialLinked=${encodeURIComponent(provider.toUpperCase())}&socialMock=1`;
          if (url !== expectedCallback) {
            throw new Error("데모 소셜 계정 연결 결과를 확인할 수 없습니다.");
          }
          void navigate(url);
          return;
        }
        const providerId = provider.toLowerCase() as NativeOAuthProvider;
        const authorizationUrl = validateAuthorizationUrl(providerId, url);
        if (!authorizationUrl) {
          throw new Error("소셜 로그인 제공자 주소를 확인할 수 없습니다.");
        }
        await Browser.open({ url: authorizationUrl });
        return;
      }
      window.location.href = url;
    } catch (e) {
      setError(e instanceof Error ? e.message : "소셜 계정 연결을 시작하지 못했습니다.");
      setSocialBusy(null);
    }
  };

  const unlinkProvider = async (provider: string) => {
    setSocialBusy(provider);
    setError(null);
    setMessage(null);
    try {
      setInfo(await unlinkSocial(provider));
      setMessage(`${PROVIDER_LABELS[provider] ?? provider} 계정 연결을 해제했습니다.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "소셜 계정 연결 해제에 실패했습니다.");
    } finally {
      setSocialBusy(null);
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
                className="max-w-xs"
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

        {/* 전화번호 — 인증하기(인증 성공 시 번호 저장 + 인증 상태 통합) */}
        <div className="space-y-2">
          <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <Phone className="size-3.5" />
            전화번호
          </span>

          {/* Firebase invisible reCAPTCHA 앵커 — 화면에 보이지 않지만 발송 시점에 DOM 에 있어야 한다. */}
          {phoneVerify.isFirebase && <div id={RECAPTCHA_CONTAINER_ID} />}

          <div className="flex flex-wrap items-center gap-2">
            <Input
              className="max-w-xs"
              value={phoneVerify.phone}
              onChange={(e) => phoneVerify.setPhone(e.target.value)}
              placeholder="010-1234-5678"
              inputMode="tel"
              disabled={phoneVerify.requesting}
            />
            <Button
              size="sm"
              className="bg-blue-600 text-white hover:bg-blue-700"
              onClick={() => void phoneVerify.handleRequest()}
              disabled={phoneVerify.requesting || phoneVerify.cooldown > 0}
            >
              {phoneVerify.requesting ? (
                <>
                  <Loader2 className="mr-1 size-4 animate-spin" />
                  발송 중...
                </>
              ) : phoneVerify.cooldown > 0 ? (
                `재발송 (${phoneVerify.cooldown}s)`
              ) : phoneVerify.codeSent ? (
                "재발송"
              ) : phoneVerify.status?.phoneVerified ? (
                "재인증"
              ) : (
                "인증하기"
              )}
            </Button>
            {phoneVerify.status?.phoneVerified ? (
              <Badge className="bg-green-100 text-green-700">
                <CheckCircle2 className="mr-1 size-3.5" />
                인증됨
              </Badge>
            ) : phoneVerify.status?.phone || info?.phone ? (
              <Badge className="bg-slate-100 text-slate-600">미인증</Badge>
            ) : null}
          </div>

          {/* Mock(데모) 안내 배지 */}
          {phoneVerify.devCode && (
            <div className="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
              <Sparkles className="size-4 shrink-0" />
              <span>
                데모 모드(실 제공자 키 미설정)로 발송된 인증번호:{" "}
                <span className="font-mono font-bold tracking-widest">{phoneVerify.devCode}</span>
              </span>
            </div>
          )}

          {/* 코드 입력 + 검증 */}
          {phoneVerify.codeSent && (
            <div className="space-y-2">
              <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
                <ShieldCheck className="size-3.5" />
                인증번호
                {phoneVerify.provider && (
                  <span className="font-normal text-slate-400">
                    · {phoneVerify.realSending ? `${phoneVerify.provider} 실발송` : `${phoneVerify.provider} 데모`}
                  </span>
                )}
              </span>
              <div className="flex flex-wrap items-center gap-2">
                <Input
                  className="max-w-[10rem] font-mono tracking-widest"
                  value={phoneVerify.code}
                  onChange={(e) => phoneVerify.setCode(e.target.value.replace(/[^0-9]/g, "").slice(0, 6))}
                  placeholder="6자리 코드"
                  inputMode="numeric"
                  maxLength={6}
                  disabled={phoneVerify.verifying}
                />
                <Button
                  size="sm"
                  className="bg-blue-600 text-white hover:bg-blue-700"
                  onClick={() => void phoneVerify.handleVerify()}
                  disabled={phoneVerify.verifying || phoneVerify.code.trim().length === 0}
                >
                  {phoneVerify.verifying ? (
                    <>
                      <Loader2 className="mr-1 size-4 animate-spin" />
                      확인 중...
                    </>
                  ) : (
                    "확인"
                  )}
                </Button>
              </div>
            </div>
          )}

          {/* 전화번호 인증 전용 안내 — 계정 상단 메시지와 분리 */}
          {phoneVerify.error && <p className="text-xs text-red-600">{phoneVerify.error}</p>}
          {phoneVerify.message && !phoneVerify.error && <p className="text-xs text-green-600">{phoneVerify.message}</p>}
        </div>

        {/* 연결된 소셜 계정 */}
        <div className="space-y-2">
          <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <Link2 className="size-3.5" />
            연결된 계정
          </span>
          <div className="flex flex-wrap gap-2">
            {["GOOGLE", "KAKAO", "NAVER"].map((provider) => {
              const linked = info?.linkedProviders.includes(provider) ?? false;
              const availabilityKey = provider.toLowerCase() as SocialProvider;
              const providerAvailable = oauthProviders[availabilityKey];
              return (
                <div key={provider} className="flex items-center gap-1 rounded-lg border border-slate-200 bg-card px-2 py-1">
                  <Badge className={linked ? "bg-blue-50 text-blue-700" : "bg-slate-100 text-slate-500"}>
                    {PROVIDER_LABELS[provider] ?? provider}
                  </Badge>
                  {linked ? (
                    <Button
                      size="sm"
                      variant="ghost"
                      className="h-7 px-2 text-xs"
                      disabled={socialBusy !== null || socialOAuthBlocked}
                      onClick={() => void unlinkProvider(provider)}
                      title={socialOAuthBlocked ? "운영 서비스 복구 후 연결을 해제할 수 있습니다." : undefined}
                    >
                      해제
                    </Button>
                  ) : (
                    <Button
                      size="sm"
                      variant="ghost"
                      className="h-7 px-2 text-xs"
                      disabled={socialBusy !== null || socialOAuthBlocked || oauthProvidersLoading || !providerAvailable}
                      onClick={() => void linkProvider(provider)}
                      title={socialOAuthBlocked
                        ? "운영 서비스 복구 후 새 소셜 계정을 연결할 수 있습니다."
                        : !oauthProvidersLoading && !providerAvailable
                          ? `${PROVIDER_LABELS[provider] ?? provider} 로그인 설정이 완료되지 않았습니다.`
                          : undefined}
                    >
                      연결
                    </Button>
                  )}
                </div>
              );
            })}
          </div>
          {socialOAuthBlocked && (
            <p className="text-xs font-medium text-amber-700 dark:text-amber-300">
              장애 체험 중에는 소셜 계정 연결을 변경할 수 없습니다.
            </p>
          )}
          {!socialOAuthBlocked && oauthProvidersError && (
            <p className="text-xs font-medium text-amber-700 dark:text-amber-300">
              소셜 로그인 상태를 확인하지 못해 새 계정 연결을 잠시 사용할 수 없습니다.
            </p>
          )}
          {!socialOAuthBlocked && !oauthProvidersLoading && !oauthProvidersError
            && ["GOOGLE", "KAKAO", "NAVER"].some((provider) => !oauthProviders[provider.toLowerCase() as SocialProvider]) && (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                설정이 완료된 소셜 제공자만 새로 연결할 수 있습니다.
              </p>
            )}
          <p className="text-xs text-slate-400">
            남는 로그인 수단이 없으면 해제가 제한됩니다.
          </p>
        </div>
      </CardContent>
    </Card>
  );
}
