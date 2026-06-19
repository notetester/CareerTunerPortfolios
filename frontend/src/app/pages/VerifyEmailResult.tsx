import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { CheckCircle2, XCircle, Loader2 } from "lucide-react";
import { resendVerificationEmail } from "../auth/authApi";

/** 이메일 인증 결과 페이지. 백엔드 verify-email 이 ?success=true|false 로 리다이렉트한다. */
export function VerifyEmailResultPage() {
  const [params] = useSearchParams();
  const success = params.get("success") === "true";
  const [email, setEmail] = useState("");
  const [sending, setSending] = useState(false);
  const [resent, setResent] = useState(false);
  const [resendError, setResendError] = useState<string | null>(null);

  const handleResend = async () => {
    setResendError(null);
    if (!/.+@.+\..+/.test(email.trim())) {
      setResendError("가입한 이메일을 입력해 주세요.");
      return;
    }
    setSending(true);
    try {
      await resendVerificationEmail(email.trim());
      setResent(true);
    } catch {
      setResendError("재발송에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-120px)] flex items-center justify-center px-4">
      <div className="w-full max-w-md text-center bg-card border border-slate-200 rounded-2xl shadow-sm p-8 space-y-4">
        {success ? (
          <CheckCircle2 className="size-14 text-green-600 mx-auto" />
        ) : (
          <XCircle className="size-14 text-red-500 mx-auto" />
        )}
        <h1 className="text-xl font-bold text-slate-900">
          {success ? "이메일 인증이 완료되었습니다" : "인증에 실패했습니다"}
        </h1>
        <p className="text-sm text-slate-500">
          {success
            ? "이제 CareerTuner의 모든 기능을 이용할 수 있습니다."
            : "링크가 만료되었거나 이미 사용되었습니다. 아래에서 인증 메일을 다시 받아보세요."}
        </p>

        {!success && (
          resent ? (
            <p className="rounded-lg bg-green-50 px-3 py-2 text-sm text-green-700">
              인증 메일을 다시 보냈습니다. 메일함을 확인해 주세요.
            </p>
          ) : (
            <div className="space-y-2 text-left">
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="가입한 이메일"
                className="h-11 w-full rounded-xl border border-slate-200 px-3 text-sm outline-none focus:border-blue-400"
              />
              {resendError && <p className="text-xs text-red-600">{resendError}</p>}
              <button
                type="button"
                onClick={() => void handleResend()}
                disabled={sending}
                className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-foreground text-sm font-semibold text-background disabled:opacity-70"
              >
                {sending && <Loader2 className="size-4 animate-spin" />}
                인증 메일 다시 보내기
              </button>
            </div>
          )
        )}

        <Link
          to={success ? "/dashboard" : "/login"}
          className="inline-block mt-2 px-5 py-2.5 rounded-xl bg-primary text-white text-sm font-semibold"
        >
          {success ? "대시보드로 이동" : "로그인으로 이동"}
        </Link>
      </div>
    </div>
  );
}
