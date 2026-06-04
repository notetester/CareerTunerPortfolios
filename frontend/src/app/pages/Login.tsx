import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Input } from "../components/ui/input";
import { Sparkles, Mail, Lock, Eye, EyeOff, CheckCircle2, ArrowRight } from "lucide-react";

export function LoginPage() {
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [showPassword, setShowPassword] = useState(false);
  const [userType, setUserType] = useState<string>("취준생");
  const navigate = useNavigate();

  const handleSubmit = () => {
    navigate("/dashboard");
  };

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
                onClick={() => setMode("login")}
                className={`flex-1 py-2 rounded-lg text-sm font-semibold transition-colors ${mode === "login" ? "bg-white shadow-sm text-slate-900" : "text-slate-500"}`}
              >
                로그인
              </button>
              <button
                onClick={() => setMode("signup")}
                className={`flex-1 py-2 rounded-lg text-sm font-semibold transition-colors ${mode === "signup" ? "bg-white shadow-sm text-slate-900" : "text-slate-500"}`}
              >
                회원가입
              </button>
            </div>

            {/* Social login */}
            <div className="space-y-2.5">
              {[
                { label: "Google로 계속하기", emoji: "🔵" },
                { label: "카카오로 계속하기", emoji: "🟡" },
                { label: "네이버로 계속하기", emoji: "🟢" },
              ].map((s) => (
                <Button
                  key={s.label}
                  variant="outline"
                  className="w-full h-11 text-sm font-medium"
                  onClick={handleSubmit}
                >
                  <span className="mr-2">{s.emoji}</span>
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
            <div className="space-y-3">
              {mode === "signup" && (
                <Input placeholder="이름" className="h-11" />
              )}
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                <Input placeholder="이메일" className="pl-9 h-11" type="email" />
              </div>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                <Input
                  placeholder="비밀번호"
                  className="pl-9 pr-10 h-11"
                  type={showPassword ? "text" : "password"}
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
                  <Input placeholder="비밀번호 확인" className="h-11" type="password" />
                  <div>
                    <div className="text-xs text-slate-500 mb-2">사용자 유형 선택</div>
                    <div className="flex gap-2 flex-wrap">
                      {["취준생", "이직자", "경력자"].map((type) => (
                        <button
                          key={type}
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
                  <button className="text-xs text-blue-600 hover:text-blue-700">비밀번호를 잊으셨나요?</button>
                </div>
              )}
            </div>

            <Button
              className="w-full h-11 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-base font-semibold gap-2"
              onClick={handleSubmit}
            >
              {mode === "login" ? "로그인" : "무료 회원가입"}
              <ArrowRight className="size-4" />
            </Button>

            {mode === "signup" && (
              <p className="text-xs text-slate-400 text-center">
                가입하면 <a href="#" className="text-blue-600 hover:underline">이용약관</a>과{" "}
                <a href="#" className="text-blue-600 hover:underline">개인정보처리방침</a>에 동의하게 됩니다.
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
