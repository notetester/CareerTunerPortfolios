import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Input } from "../components/ui/input";
import { ApiError } from "../lib/api";
import { Sparkles, Mail, Lock, Eye, EyeOff, CheckCircle2, ArrowRight, Loader2 } from "lucide-react";

export function LoginPage() {
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [showPassword, setShowPassword] = useState(false);
  const [userType, setUserType] = useState<string>("취준생");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const { login, register, socialLogin } = useAuth();

  const switchMode = (nextMode: "login" | "signup") => {
    setMode(nextMode);
    setError(null);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);

    const normalizedEmail = email.trim();
    if (!normalizedEmail || !password) {
      setError("이메일과 비밀번호를 입력해 주세요.");
      return;
    }
    if (mode === "signup") {
      if (!name.trim()) {
        setError("이름을 입력해 주세요.");
        return;
      }
      if (password !== passwordConfirm) {
        setError("비밀번호 확인이 일치하지 않습니다.");
        return;
      }
    }

    try {
      setSubmitting(true);
      if (mode === "login") {
        await login(normalizedEmail, password);
      } else {
        await register(normalizedEmail, password, name.trim());
      }
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "인증 요청에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  };

  const socialButtons = [
    { label: "Google로 계속하기", provider: "google" as const, mark: "G", className: "bg-blue-600" },
    { label: "카카오로 계속하기", provider: "kakao" as const, mark: "K", className: "bg-yellow-400 text-slate-900" },
    { label: "네이버로 계속하기", provider: "naver" as const, mark: "N", className: "bg-green-600" },
  ];

  return (
    <div className="min-h-[calc(100vh-120px)] bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-50 flex items-center justify-center py-12 px-4">
      <div className="w-full max-w-4xl grid md:grid-cols-2 gap-8 items-center">
        {/* Left: Brand message */}
        <div className="hidden md:block space-y-6">
          <div className="flex items-center gap-2.5">
            <div className="size-10 rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center shadow-md">
              <Sparkles className="size-5 text-white" />
            </div>
            <span className="text-2xl font-black bg-gradient-to-r from-blue-600 to-indigo-600 bg-clip-text text-transparent">CareerTuner</span>
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
          <div className="bg-white rounded-2xl border border-slate-200 p-4 shadow-sm">
            <div className="text-xs text-slate-500 mb-2">가입 후 즉시 이용 가능</div>
            <div className="flex items-center gap-2">
              <Badge className="bg-green-100 text-green-700">무료 플랜</Badge>
              <span className="text-sm text-slate-600">공고 분석 3회 + 모의면접 1회 제공</span>
            </div>
          </div>
        </div>

        {/* Right: Form */}
        <Card className="border border-slate-200 shadow-xl bg-white">
          <CardContent className="p-8 space-y-6">
            {/* Toggle */}
            <div className="flex bg-slate-100 rounded-xl p-1">
              <button
                onClick={() => switchMode("login")}
                className={`flex-1 py-2 rounded-lg text-sm font-semibold transition-colors ${mode === "login" ? "bg-white shadow-sm text-slate-900" : "text-slate-500"}`}
              >
                로그인
              </button>
              <button
                onClick={() => switchMode("signup")}
                className={`flex-1 py-2 rounded-lg text-sm font-semibold transition-colors ${mode === "signup" ? "bg-white shadow-sm text-slate-900" : "text-slate-500"}`}
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
                >
                  <span className={`mr-2 inline-flex size-5 items-center justify-center rounded-full text-[11px] font-black text-white ${s.className}`}>
                    {s.mark}
                  </span>
                  {s.label}
                </Button>
              ))}
            </div>

            <div className="flex items-center gap-3">
              <div className="flex-1 h-px bg-slate-200" />
              <span className="text-xs text-slate-400">또는 이메일로</span>
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
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                <Input
                  placeholder="이메일"
                  className="pl-9 h-11"
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  autoComplete="email"
                />
              </div>
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
                </>
              )}
              {mode === "login" && (
                <div className="flex justify-end">
                  <button type="button" className="text-xs text-blue-600 hover:text-blue-700">비밀번호를 잊으셨나요?</button>
                </div>
              )}

              {error && (
                <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                  {error}
                </div>
              )}

              <Button
                type="submit"
                disabled={submitting}
                className="w-full h-11 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-base font-semibold gap-2 disabled:cursor-not-allowed disabled:opacity-70"
              >
                {submitting && <Loader2 className="size-4 animate-spin" />}
                {mode === "login" ? "로그인" : "무료 회원가입"}
                {!submitting && <ArrowRight className="size-4" />}
              </Button>
            </form>

            {mode === "signup" && (
              <p className="text-xs text-slate-400 text-center">
                가입하면 <Link to="/legal/terms" className="text-blue-600 hover:underline">이용약관</Link>과{" "}
                <Link to="/legal/privacy" className="text-blue-600 hover:underline">개인정보처리방침</Link>에 동의하게 됩니다.
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
