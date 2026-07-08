import { FormEvent, useState, type ReactNode } from "react";
import { Link } from "react-router";
import { ArrowLeft, Loader2, Mail } from "lucide-react";
import { requestPasswordReset } from "../auth/authApi";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Input } from "../components/ui/input";

export function ForgotPasswordPage() {
  const [identifier, setIdentifier] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    const normalizedIdentifier = identifier.trim();
    if (!normalizedIdentifier) {
      setError("아이디 또는 이메일을 입력해 주세요.");
      return;
    }

    try {
      setSubmitting(true);
      await requestPasswordReset(normalizedIdentifier);
      setSent(true);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "재설정 메일 요청에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthActionLayout
      title="비밀번호 찾기"
      description="아이디 또는 이메일을 입력하면 확인 가능한 계정의 이메일로 재설정 링크를 전송합니다."
    >
      {sent ? (
        <div className="space-y-4 text-center">
          <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
            입력한 정보와 일치하는 확인 가능한 계정이 있으면 가입된 이메일로 재설정 링크를 보냅니다. 메일의 링크를 열어 새 비밀번호를 설정해 주세요.
          </div>
          <Button asChild className="w-full bg-blue-600 text-white hover:bg-blue-700">
            <Link to="/login">로그인으로 돌아가기</Link>
          </Button>
        </div>
      ) : (
        <form className="space-y-4" onSubmit={handleSubmit}>
          <div className="relative">
            <Mail className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input
              className="h-11 pl-9"
              type="text"
              placeholder="아이디 또는 이메일"
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
              autoComplete="username"
            />
          </div>
          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
          <Button type="submit" disabled={submitting} className="h-11 w-full bg-blue-600 text-white hover:bg-blue-700">
            {submitting && <Loader2 className="size-4 animate-spin" />}
            재설정 메일 받기
          </Button>
        </form>
      )}
      <Link to="/login" className="inline-flex items-center gap-1 text-sm font-medium text-slate-500 hover:text-blue-600">
        <ArrowLeft className="size-4" />
        로그인으로 돌아가기
      </Link>
    </AuthActionLayout>
  );
}

export function AuthActionLayout({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <div className="flex min-h-[calc(100vh-120px)] items-center justify-center bg-slate-50 px-4 py-12">
      <Card className="w-full max-w-md border border-slate-200 bg-card shadow-xl">
        <CardContent className="space-y-6 p-8">
          <div className="space-y-2 text-center">
            <div className="mx-auto flex size-11 items-center justify-center rounded-xl bg-blue-600 text-lg font-black text-white">
              CT
            </div>
            <h1 className="text-2xl font-black text-slate-950">{title}</h1>
            <p className="text-sm text-slate-500">{description}</p>
          </div>
          <div className="space-y-4">{children}</div>
        </CardContent>
      </Card>
    </div>
  );
}
