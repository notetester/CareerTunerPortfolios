import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { Loader2, Mail } from "lucide-react";
import { releaseDormant, requestDormantRelease } from "../auth/authApi";
import { useAuth } from "../auth/AuthContext";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { setTokens } from "../lib/tokenStore";
import { AuthActionLayout } from "./ForgotPassword";

export function ReleaseDormantPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { refreshMe } = useAuth();
  const token = params.get("token") ?? "";
  const [email, setEmail] = useState(params.get("email") ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [releasing, setReleasing] = useState(!!token);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;

    let cancelled = false;
    setReleasing(true);
    releaseDormant(token)
      .then(async (response) => {
        if (cancelled) return;
        setTokens({ accessToken: response.accessToken, refreshToken: response.refreshToken });
        await refreshMe();
        navigate("/dashboard", { replace: true });
      })
      .catch((requestError) => {
        if (cancelled) return;
        setError(requestError instanceof Error ? requestError.message : "휴면 해제에 실패했습니다.");
      })
      .finally(() => {
        if (!cancelled) setReleasing(false);
      });

    return () => {
      cancelled = true;
    };
  }, [navigate, refreshMe, token]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    const normalizedEmail = email.trim();
    if (!normalizedEmail) {
      setError("이메일을 입력해 주세요.");
      return;
    }

    try {
      setSubmitting(true);
      await requestDormantRelease(normalizedEmail);
      setSent(true);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "휴면 해제 메일 요청에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthActionLayout
      title="휴면 계정 해제"
      description="가입한 이메일 인증을 완료하면 휴면 상태가 해제됩니다."
    >
      {releasing ? (
        <div className="flex items-center justify-center gap-2 rounded-lg bg-slate-50 px-4 py-6 text-sm text-slate-600">
          <Loader2 className="size-4 animate-spin" />
          휴면 해제 처리 중입니다.
        </div>
      ) : sent ? (
        <div className="space-y-4 text-center">
          <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
            입력한 이메일로 휴면 해제 링크를 보냈습니다. 메일의 링크를 열어 계정을 활성화해 주세요.
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
              type="email"
              placeholder="이메일"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
            />
          </div>
          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
          <Button type="submit" disabled={submitting} className="h-11 w-full bg-blue-600 text-white hover:bg-blue-700">
            {submitting && <Loader2 className="size-4 animate-spin" />}
            휴면 해제 메일 받기
          </Button>
          <Button asChild variant="outline" className="w-full">
            <Link to="/login">로그인으로 돌아가기</Link>
          </Button>
        </form>
      )}
    </AuthActionLayout>
  );
}
