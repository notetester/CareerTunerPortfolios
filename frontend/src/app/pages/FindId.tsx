import { FormEvent, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { ArrowLeft, Loader2, Mail, UserRound } from "lucide-react";
import { requestFindId, verifyFindId } from "../auth/authApi";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { AuthActionLayout } from "./ForgotPassword";

export function FindIdPage() {
  const [params] = useSearchParams();
  const token = params.get("token");

  if (token) {
    return <FindIdResult token={token} />;
  }
  return <FindIdRequest />;
}

function FindIdRequest() {
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    const normalizedEmail = email.trim();
    if (!/.+@.+\..+/.test(normalizedEmail)) {
      setError("가입할 때 인증한 이메일을 입력해 주세요.");
      return;
    }

    try {
      setSubmitting(true);
      await requestFindId(normalizedEmail);
      setSent(true);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "아이디 확인 메일 요청에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthActionLayout
      title="아이디 찾기"
      description="인증된 이메일로 아이디 확인 링크를 전송합니다."
    >
      {sent ? (
        <div className="space-y-4 text-center">
          <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
            입력한 이메일과 일치하는 확인 가능한 계정이 있으면 아이디 확인 링크를 보냅니다.
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
              placeholder="인증된 이메일"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
            />
          </div>
          {error && <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
          <Button type="submit" disabled={submitting} className="h-11 w-full bg-blue-600 text-white hover:bg-blue-700">
            {submitting && <Loader2 className="size-4 animate-spin" />}
            확인 메일 받기
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

function FindIdResult({ token }: { token: string }) {
  const [loginId, setLoginId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    verifyFindId(token)
      .then((result) => {
        if (!cancelled) setLoginId(result.loginId);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "아이디 확인 링크가 만료되었거나 유효하지 않습니다.");
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <AuthActionLayout
      title="아이디 확인"
      description="보안을 위해 전체 아이디 대신 일부만 표시합니다."
    >
      {loginId ? (
        <div className="space-y-4 text-center">
          <div className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-5">
            <div className="mb-2 flex justify-center text-blue-600">
              <UserRound className="size-6" />
            </div>
            <p className="text-xs font-semibold text-blue-700">로그인 아이디</p>
            <p className="mt-1 text-xl font-black text-slate-950">{loginId}</p>
          </div>
          <Button asChild className="w-full bg-blue-600 text-white hover:bg-blue-700">
            <Link to="/login">로그인하기</Link>
          </Button>
        </div>
      ) : error ? (
        <div className="space-y-4 text-center">
          <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
          <Button asChild variant="outline" className="w-full">
            <Link to="/auth/find-id">다시 요청하기</Link>
          </Button>
        </div>
      ) : (
        <p className="text-center text-sm text-slate-500">아이디 정보를 확인하는 중...</p>
      )}
    </AuthActionLayout>
  );
}
