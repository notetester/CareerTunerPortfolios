import { FormEvent, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { Loader2, Lock } from "lucide-react";
import { resetPassword } from "../auth/authApi";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { AuthActionLayout } from "./ForgotPassword";

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);

    if (!token) {
      setError("재설정 토큰이 없습니다. 비밀번호 찾기를 다시 진행해 주세요.");
      return;
    }
    if (password.length < 8) {
      setError("비밀번호는 8자 이상 입력해 주세요.");
      return;
    }
    if (password !== passwordConfirm) {
      setError("비밀번호 확인이 일치하지 않습니다.");
      return;
    }

    try {
      setSubmitting(true);
      await resetPassword(token, password);
      setDone(true);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "비밀번호 재설정에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthActionLayout
      title="새 비밀번호 설정"
      description="이메일 인증 링크가 확인되면 새 비밀번호로 변경할 수 있습니다."
    >
      {done ? (
        <div className="space-y-4 text-center">
          <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
            비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.
          </div>
          <Button asChild className="w-full bg-blue-600 text-white hover:bg-blue-700">
            <Link to="/login">로그인하기</Link>
          </Button>
        </div>
      ) : (
        <form className="space-y-4" onSubmit={handleSubmit}>
          <PasswordInput
            value={password}
            onChange={setPassword}
            placeholder="새 비밀번호"
            autoComplete="new-password"
          />
          <PasswordInput
            value={passwordConfirm}
            onChange={setPasswordConfirm}
            placeholder="새 비밀번호 확인"
            autoComplete="new-password"
          />
          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
          <Button type="submit" disabled={submitting || !token} className="h-11 w-full bg-blue-600 text-white hover:bg-blue-700">
            {submitting && <Loader2 className="size-4 animate-spin" />}
            비밀번호 변경
          </Button>
          {!token && (
            <Button asChild variant="outline" className="w-full">
              <Link to="/auth/forgot-password">재설정 메일 다시 받기</Link>
            </Button>
          )}
        </form>
      )}
    </AuthActionLayout>
  );
}

function PasswordInput({
  value,
  onChange,
  placeholder,
  autoComplete,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  autoComplete: string;
}) {
  return (
    <div className="relative">
      <Lock className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
      <Input
        className="h-11 pl-9"
        type="password"
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
      />
    </div>
  );
}
