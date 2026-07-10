import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Checkbox } from "../components/ui/checkbox";
import { Input } from "../components/ui/input";
import { ApiError } from "../lib/api";
import { useOutageFallback } from "../lib/outageFallback";
import { checkEmailDuplicate, checkLoginIdDuplicate } from "../auth/authApi";
import { Sparkles, Mail, Lock, Eye, EyeOff, CheckCircle2, ArrowRight, Loader2, UserRound } from "lucide-react";

export function LoginPage() {
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [showPassword, setShowPassword] = useState(false);
  const [userType, setUserType] = useState<string>("취준생");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [termsAgreed, setTermsAgreed] = useState(false);
  const [privacyAgreed, setPrivacyAgreed] = useState(false);
  const [aiDataAgreed, setAiDataAgreed] = useState(false);
  const [resumeAnalysisAgreed, setResumeAnalysisAgreed] = useState(false);
  const [marketingAgreed, setMarketingAgreed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dormantEmail, setDormantEmail] = useState<string | null>(null);
  const [emailDuplicate, setEmailDuplicate] = useState(false);
  const [loginIdDuplicate, setLoginIdDuplicate] = useState(false);
  const navigate = useNavigate();
  const { login, register, socialLogin } = useAuth();
  const { socialOAuthBlocked } = useOutageFallback();

  const switchMode = (nextMode: "login" | "signup") => {
    setMode(nextMode);
    setError(null);
    setEmailDuplicate(false);
    setLoginIdDuplicate(false);
  };

  // 회원가입 시 이메일 입력을 벗어나면 중복 여부를 미리 확인한다(가입 전 즉시 피드백).
  const handleEmailBlur = async () => {
    const normalized = email.trim();
    if (mode !== "signup" || !/.+@.+\..+/.test(normalized)) {
      setEmailDuplicate(false);
      return;
    }
    try {
      const { duplicate } = await checkEmailDuplicate(normalized);
      setEmailDuplicate(duplicate);
    } catch {
      setEmailDuplicate(false);
    }
  };

  const handleLoginIdBlur = async () => {
    const normalized = loginId.trim().toLowerCase();
    if (mode !== "signup" || !normalized) {
      setLoginIdDuplicate(false);
      return;
    }
    if (!/^[a-z0-9_]{4,50}$/.test(normalized)) {
      setLoginIdDuplicate(false);
      return;
    }
    try {
      const { duplicate } = await checkLoginIdDuplicate(normalized);
      setLoginIdDuplicate(duplicate);
    } catch {
      setLoginIdDuplicate(false);
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setDormantEmail(null);

    const loginIdentifier = email.trim();
    if (mode === "login" && (!loginIdentifier || !password)) {
      setError("아이디 또는 이메일과 비밀번호를 입력해 주세요.");
      return;
    }
    if (mode === "signup" && !password) {
      setError("비밀번호를 입력해 주세요.");
      return;
    }
    if (mode === "signup") {
      const normalizedLoginId = loginId.trim().toLowerCase();
      const signupEmail = email.trim();
      if (!name.trim()) {
        setError("이름을 입력해 주세요.");
        return;
      }
      if (!/^[a-z0-9_]{4,50}$/.test(normalizedLoginId)) {
        setError("아이디는 영문 소문자, 숫자, 밑줄 4~50자로 입력해 주세요.");
        return;
      }
      if (password !== passwordConfirm) {
        setError("비밀번호 확인이 일치하지 않습니다.");
        return;
      }
      if (!termsAgreed || !privacyAgreed) {
        setError("필수 약관과 개인정보 처리방침에 동의해 주세요.");
        return;
      }
      if (signupEmail && !/.+@.+\..+/.test(signupEmail)) {
        setError("이메일을 입력하려면 올바른 이메일 형식으로 입력해 주세요. 이메일은 나중에 등록할 수도 있습니다.");
        return;
      }
      if (signupEmail && emailDuplicate) {
        setError("이미 사용 중인 이메일입니다. 다른 이메일을 사용해 주세요.");
        return;
      }
      if (loginIdDuplicate) {
        setError("이미 사용 중인 아이디입니다. 다른 아이디를 사용해 주세요.");
        return;
      }
    }

    try {
      setSubmitting(true);
      if (mode === "login") {
        const result = await login(loginIdentifier, password);
        if (result.mfaRequired && result.challengeToken) {
          const returnTo = new URLSearchParams(window.location.search).get("returnTo");
          const safeReturnTo = returnTo && returnTo.startsWith("/") && !returnTo.startsWith("//") ? returnTo : "/dashboard";
          navigate(`/auth/mfa?challengeToken=${encodeURIComponent(result.challengeToken)}&returnTo=${encodeURIComponent(safeReturnTo)}`, { replace: true });
          return;
        }
      } else {
        const normalizedLoginId = loginId.trim().toLowerCase();
        await register(normalizedLoginId, email.trim() || null, password, name.trim(), {
          termsAgreed,
          privacyAgreed,
          aiDataAgreed,
          resumeAnalysisAgreed,
          marketingAgreed,
        });
      }
      // ?returnTo=/... 가 있으면 로그인 후 그 화면으로 복귀(내부 경로만 허용 — open redirect 방지).
      // 폰 마이크 핸드오프(/mic-remote) 등 딥링크 진입에서 로그인 후 원래 화면으로 돌아오게 한다.
      const returnTo = new URLSearchParams(window.location.search).get("returnTo");
      const dest = returnTo && returnTo.startsWith("/") && !returnTo.startsWith("//") ? returnTo : "/dashboard";
      navigate(dest, { replace: true });
    } catch (err) {
      const message = toAuthErrorMessage(err);
      setError(message);
      if (mode === "login" && err instanceof ApiError && err.status === 403 && message.includes("휴면")) {
        setDormantEmail(loginIdentifier);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const toAuthErrorMessage = (err: unknown) => {
    if (!(err instanceof ApiError)) {
      return "인증 요청에 실패했습니다. 잠시 후 다시 시도해 주세요.";
    }
    if (err.status === 401) {
      return err.message || "아이디/이메일 또는 비밀번호가 올바르지 않습니다.";
    }
    if (err.status === 403) {
      return err.message || "현재 사용할 수 없는 계정입니다.";
    }
    if (err.status === 409) {
      return err.message || "이미 사용 중인 이메일입니다.";
    }
    return err.message || "인증 요청에 실패했습니다. 잠시 후 다시 시도해 주세요.";
  };

  const socialButtons = [
    { label: "Google로 계속하기", provider: "google" as const, mark: "G", className: "bg-blue-600" },
    { label: "카카오로 계속하기", provider: "kakao" as const, mark: "K", className: "bg-yellow-400 text-slate-900" },
    { label: "네이버로 계속하기", provider: "naver" as const, mark: "N", className: "bg-green-600" },
  ];
  const identifierPlaceholder = mode === "login" ? "아이디 또는 이메일" : "이메일 (선택)";
  const IdentifierIcon = mode === "login" ? UserRound : Mail;

  return (
    <div className="min-h-[calc(100vh-120px)] bg-muted flex items-center justify-center py-12 px-4">
      <div className="w-full max-w-4xl grid md:grid-cols-2 gap-8 items-center">
        {/* Left: Brand message */}
        <div className="hidden md:block space-y-6">
          <div className="flex items-center gap-2.5">
            <div className="size-10 rounded-xl bg-accent-soft flex items-center justify-center shadow-md">
              <Sparkles className="size-5 text-primary" />
            </div>
            <span className="text-2xl font-black text-primary">CareerTuner</span>
          </div>
          <h2 className="text-3xl font-black text-slate-900 leading-tight">
            AI와 함께<br />취업 준비를 시작하세요
          </h2>
          <div className="space-y-3">
            {[
              "공고 업로드 → AI 즉시 분석",
              "내 스펙과 정밀 비교 진단",
              "8가지 모드 AI 가상 면접",
              "답변 첨삭 + 개선 방향 제시",
              "장기 취업 경향 분석",
            ].map((t) => (
              <div key={t} className="flex items-center gap-2 text-slate-700">
                <CheckCircle2 className="size-4 text-green-600 flex-shrink-0" />
                <span className="text-sm">{t}</span>
              </div>
            ))}
          </div>
          <div className="bg-card rounded-2xl border border-slate-200 p-4 shadow-sm">
            <div className="text-xs text-slate-500 mb-2">가입 후 즉시 이용 가능</div>
            <div className="flex items-center gap-2">
              <Badge className="bg-green-100 text-green-700">무료 플랜</Badge>
              <span className="text-sm text-slate-600">공고 분석 3회 + 모의면접 1회 제공</span>
            </div>
          </div>
        </div>

        {/* Right: Form */}
        <Card className="border border-slate-200 shadow-xl bg-card">
          <CardContent className="p-8 space-y-6">
            {/* Toggle */}
            <div className="flex bg-slate-100 rounded-xl p-1">
              <button
                onClick={() => switchMode("login")}
                className={`flex-1 py-2 rounded-lg text-sm font-semibold transition-colors ${mode === "login" ? "bg-card shadow-sm text-slate-900" : "text-slate-500"}`}
              >
                로그인
              </button>
              <button
                onClick={() => switchMode("signup")}
                className={`flex-1 py-2 rounded-lg text-sm font-semibold transition-colors ${mode === "signup" ? "bg-card shadow-sm text-slate-900" : "text-slate-500"}`}
              >
                회원가입
              </button>
            </div>

            {/* Social login */}
            <div className="space-y-2.5">
              {socialButtons.map((s) => (
                <Button
                  key={s.label}
                  variant="outline"
                  type="button"
                  className="w-full h-11 text-sm font-medium"
                  onClick={() => socialLogin(s.provider)}
                  disabled={socialOAuthBlocked}
                  title={socialOAuthBlocked ? "AWS 연결 복구 후 소셜 로그인을 이용할 수 있습니다." : undefined}
                >
                  <span className={`mr-2 inline-flex size-5 items-center justify-center rounded-full text-[11px] font-black text-white ${s.className}`}>
                    {s.mark}
                  </span>
                  {s.label}
                </Button>
              ))}
              {socialOAuthBlocked && (
                <p className="text-center text-xs font-medium text-amber-700 dark:text-amber-300">
                  장애 체험 중에는 소셜 로그인을 사용할 수 없습니다. 아이디·이메일 체험 로그인을 이용해 주세요.
                </p>
              )}
            </div>

            <div className="flex items-center gap-3">
              <div className="flex-1 h-px bg-slate-200" />
              <span className="text-xs text-slate-400">{mode === "login" ? "또는 아이디/이메일로" : "또는 아이디로"}</span>
              <div className="flex-1 h-px bg-slate-200" />
            </div>

            {/* Email form */}
            <form className="space-y-3" onSubmit={handleSubmit}>
              {mode === "signup" && (
                <Input
                  placeholder="이름"
                  className="h-11"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  autoComplete="name"
                />
              )}
              {mode === "signup" && (
                <div className="space-y-1">
                  <div className="relative">
                    <UserRound className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                    <Input
                      placeholder="로그인 아이디"
                      className="h-11 pl-9"
                      value={loginId}
                      onChange={(event) => {
                        setLoginId(event.target.value.toLowerCase());
                        setLoginIdDuplicate(false);
                      }}
                      onBlur={() => void handleLoginIdBlur()}
                      autoComplete="username"
                    />
                  </div>
                  {loginIdDuplicate && (
                    <p className="text-xs text-red-600">이미 사용 중인 아이디입니다. 다른 아이디를 사용해 주세요.</p>
                  )}
                </div>
              )}
              <div className="relative">
                <IdentifierIcon className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                <Input
                  placeholder={identifierPlaceholder}
                  className="pl-9 h-11"
                  type={mode === "login" ? "text" : "email"}
                  value={email}
                  onChange={(event) => { setEmail(event.target.value); setEmailDuplicate(false); }}
                  onBlur={() => void handleEmailBlur()}
                  autoComplete={mode === "login" ? "username" : "email"}
                />
              </div>
              {mode === "signup" && emailDuplicate && (
                <p className="-mt-1 text-xs text-red-600">이미 사용 중인 이메일입니다. 로그인하거나 다른 이메일을 사용해 주세요.</p>
              )}
              {mode === "signup" && (
                <p className="-mt-1 text-xs text-slate-500">
                  이메일은 선택입니다. 비밀번호 찾기와 아이디 찾기는 이메일 등록 및 인증 후 사용할 수 있습니다.
                </p>
              )}
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                <Input
                  placeholder="비밀번호"
                  className="pl-9 pr-10 h-11"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete={mode === "login" ? "current-password" : "new-password"}
                />
                <button
                  type="button"
                  className="absolute right-3 top-1/2 -translate-y-1/2"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? <EyeOff className="size-4 text-slate-400" /> : <Eye className="size-4 text-slate-400" />}
                </button>
              </div>
              {mode === "signup" && (
                <>
                  <Input
                    placeholder="비밀번호 확인"
                    className="h-11"
                    type="password"
                    value={passwordConfirm}
                    onChange={(event) => setPasswordConfirm(event.target.value)}
                    autoComplete="new-password"
                  />
                  <div>
                    <div className="text-xs text-slate-500 mb-2">사용자 유형 선택</div>
                    <div className="flex gap-2 flex-wrap">
                      {["취준생", "이직자", "경력자"].map((type) => (
                        <button
                          key={type}
                          type="button"
                          onClick={() => setUserType(type)}
                          className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors ${userType === type ? "bg-blue-600 text-white border-blue-600" : "border-slate-300 text-slate-600 hover:border-blue-400"}`}
                        >
                          {type}
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
                    <ConsentCheckbox
                      checked={termsAgreed}
                      onCheckedChange={setTermsAgreed}
                      label="이용약관 동의"
                      href="/legal/terms"
                      required
                    />
                    <ConsentCheckbox
                      checked={privacyAgreed}
                      onCheckedChange={setPrivacyAgreed}
                      label="개인정보 처리방침 동의"
                      href="/legal/privacy"
                      required
                    />
                    <ConsentCheckbox
                      checked={aiDataAgreed}
                      onCheckedChange={setAiDataAgreed}
                      label="AI 분석 데이터 활용 동의"
                      href="/legal/ai-data-consent"
                    />
                    <ConsentCheckbox
                      checked={resumeAnalysisAgreed}
                      onCheckedChange={setResumeAnalysisAgreed}
                      label="이력서 분석 개인정보 수집·이용 동의"
                      href="/legal/resume-analysis-consent"
                    />
                    <ConsentCheckbox
                      checked={marketingAgreed}
                      onCheckedChange={setMarketingAgreed}
                      label="마케팅 정보 수신 동의"
                      href="/legal/marketing"
                    />
                  </div>
                </>
              )}
              {mode === "login" && (
                <div className="flex justify-end gap-3">
                  <Link to="/auth/find-id" className="text-xs text-slate-500 hover:text-blue-600">
                    아이디 찾기
                  </Link>
                  <Link to="/auth/forgot-password" className="text-xs text-blue-600 hover:text-blue-700">
                    비밀번호를 잊으셨나요?
                  </Link>
                </div>
              )}

              {error && (
                <div className="space-y-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                  <div>{error}</div>
                  {dormantEmail && (
                    <Link
                      to={`/auth/release-dormant?email=${encodeURIComponent(dormantEmail)}`}
                      className="inline-flex text-xs font-semibold text-red-700 underline underline-offset-2"
                    >
                      휴면 해제 메일 요청하기
                    </Link>
                  )}
                </div>
              )}

              <Button
                type="submit"
                disabled={submitting}
                className="w-full h-11 bg-primary text-base font-semibold gap-2 disabled:cursor-not-allowed disabled:opacity-70"
              >
                {submitting && <Loader2 className="size-4 animate-spin" />}
                {mode === "login" ? "로그인" : "무료 회원가입"}
                {!submitting && <ArrowRight className="size-4" />}
              </Button>
            </form>

            {mode === "signup" && (
              <p className="text-xs text-slate-400 text-center">
                필수 동의와 선택 동의는 각각 전문을 확인할 수 있으며, 가입 후 설정에서 언제든지 철회하거나 다시 동의할 수 있습니다.
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function ConsentCheckbox({
  checked,
  onCheckedChange,
  label,
  href,
  required = false,
}: {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  label: string;
  href: string;
  required?: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-3 text-xs">
      <label className="flex min-w-0 items-center gap-2 font-medium text-slate-700">
        <Checkbox
          checked={checked}
          onCheckedChange={(nextChecked) => onCheckedChange(nextChecked === true)}
        />
        <span>{required ? <span className="text-red-500">[필수] </span> : <span className="text-slate-400">[선택] </span>}{label}</span>
      </label>
      <Link className="shrink-0 font-semibold text-blue-700 hover:underline" to={href}>전문</Link>
    </div>
  );
}
