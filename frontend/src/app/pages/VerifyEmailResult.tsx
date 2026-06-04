import { Link, useSearchParams } from "react-router";
import { CheckCircle2, XCircle } from "lucide-react";

/** 이메일 인증 결과 페이지. 백엔드 verify-email 이 ?success=true|false 로 리다이렉트한다. */
export function VerifyEmailResultPage() {
  const [params] = useSearchParams();
  const success = params.get("success") === "true";

  return (
    <div className="min-h-[calc(100vh-120px)] flex items-center justify-center px-4">
      <div className="w-full max-w-md text-center bg-white border border-slate-200 rounded-2xl shadow-sm p-8 space-y-4">
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
            : "링크가 만료되었거나 이미 사용되었습니다. 다시 인증 메일을 요청해 주세요."}
        </p>
        <Link
          to="/dashboard"
          className="inline-block mt-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 text-white text-sm font-semibold"
        >
          대시보드로 이동
        </Link>
      </div>
    </div>
  );
}
