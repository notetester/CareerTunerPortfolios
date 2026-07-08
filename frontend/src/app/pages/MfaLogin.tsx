import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { Loader2, ShieldCheck, Smartphone } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { getMfaLoginStatus, verifyMfaLogin } from "../auth/mfaApi";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";

export function MfaLoginPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { completeLogin } = useAuth();
  const challengeToken = params.get("challengeToken") ?? "";
  const returnTo = safeReturnTo(params.get("returnTo"));
  const [code, setCode] = useState("");
  const [backupCode, setBackupCode] = useState("");
  const [useBackup, setUseBackup] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState("인증 앱의 6자리 코드를 입력하거나 모바일 앱에서 로그인 요청을 승인해 주세요.");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!challengeToken) return;
    const timer = window.setInterval(async () => {
      try {
        const status = await getMfaLoginStatus(challengeToken);
        if (status.token) {
          completeLogin(status.token);
          navigate(returnTo, { replace: true });
          return;
        }
        if (status.status === "APPROVED") {
          setMessage("모바일 승인이 확인되었습니다. 로그인을 완료하는 중입니다.");
        } else if (status.status === "DENIED") {
          setError("모바일 앱에서 로그인 요청을 거절했습니다.");
        } else if (status.status === "EXPIRED") {
          setError("2단계 인증 요청이 만료되었습니다. 다시 로그인해 주세요.");
        }
      } catch {
        // 폴링 실패는 사용자가 코드 입력을 계속할 수 있도록 조용히 무시한다.
      }
    }, 3000);
    return () => window.clearInterval(timer);
  }, [challengeToken, completeLogin, navigate, returnTo]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const result = await verifyMfaLogin(challengeToken, useBackup ? "" : code, useBackup ? backupCode : "");
      if (result.token) {
        completeLogin(result.token);
        navigate(returnTo, { replace: true });
      } else {
        setError("2단계 인증은 완료됐지만 토큰을 받지 못했습니다. 다시 로그인해 주세요.");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "2단계 인증에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  if (!challengeToken) {
    return (
      <div className="mx-auto flex min-h-[calc(100vh-120px)] max-w-md items-center px-4">
        <Card className="w-full border border-slate-200">
          <CardContent className="space-y-3 p-6 text-sm text-slate-600">
            <p>2단계 인증 요청 정보가 없습니다.</p>
            <Link className="font-semibold text-blue-600" to="/login">로그인으로 돌아가기</Link>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto flex min-h-[calc(100vh-120px)] max-w-md items-center px-4 py-10">
      <Card className="w-full border border-slate-200 shadow-lg">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <ShieldCheck className="size-5 text-blue-600" />
            2단계 인증
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="rounded-lg border border-blue-100 bg-blue-50 p-3 text-sm leading-6 text-blue-800">
            {message}
          </div>
          <form className="space-y-3" onSubmit={submit}>
            {!useBackup ? (
              <Input
                inputMode="numeric"
                maxLength={6}
                placeholder="6자리 인증 코드"
                value={code}
                onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
              />
            ) : (
              <Input
                placeholder="백업 코드 예: A1B2-C3D4"
                value={backupCode}
                onChange={(event) => setBackupCode(event.target.value.toUpperCase())}
              />
            )}
            {error && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
            <Button className="w-full gap-2 bg-blue-600 text-white hover:bg-blue-700" disabled={submitting}>
              {submitting && <Loader2 className="size-4 animate-spin" />}
              인증하고 로그인
            </Button>
          </form>
          <div className="flex flex-col gap-2 text-sm">
            <button type="button" className="font-semibold text-blue-600" onClick={() => setUseBackup((value) => !value)}>
              {useBackup ? "인증 앱 코드 사용하기" : "백업 코드로 인증하기"}
            </button>
            <Link to="/m/mfa-approvals" className="inline-flex items-center gap-2 font-semibold text-slate-700">
              <Smartphone className="size-4" />
              모바일 승인 화면 열기
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function safeReturnTo(value: string | null) {
  return value && value.startsWith("/") && !value.startsWith("//") ? value : "/dashboard";
}
