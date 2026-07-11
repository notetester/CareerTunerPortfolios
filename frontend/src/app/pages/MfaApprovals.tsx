import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { CheckCircle2, Loader2, RefreshCw, ShieldCheck, XCircle } from "lucide-react";
import { approveMfaPush, getPendingMfaPush, type MfaChallengeResponse } from "../auth/mfaApi";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";

export function MfaApprovalsPage() {
  const [params] = useSearchParams();
  const targetToken = params.get("challengeToken") ?? "";
  const [items, setItems] = useState<MfaChallengeResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await getPendingMfaPush());
    } catch (err) {
      setError(err instanceof Error ? err.message : "승인 요청을 불러오지 못했습니다. 앱에 로그인되어 있는지 확인해 주세요.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 5000);
    return () => window.clearInterval(timer);
  }, []);

  const sortedItems = useMemo(() => {
    if (!targetToken) return items;
    return [...items].sort((a, b) => {
      if (a.challengeToken === targetToken) return -1;
      if (b.challengeToken === targetToken) return 1;
      return 0;
    });
  }, [items, targetToken]);

  const decide = async (challengeToken: string, approve: boolean) => {
    setLoading(true);
    setError(null);
    try {
      await approveMfaPush(challengeToken, approve, navigator.userAgent || "mobile-app");
      setMessage(approve ? "로그인 요청을 승인했습니다." : "로그인 요청을 거절했습니다.");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "승인 처리에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto min-h-[calc(100vh-120px)] max-w-2xl px-4 py-8">
      <Card className="border border-slate-200 shadow-sm">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <ShieldCheck className="size-5 text-blue-600" />
            모바일 로그인 승인
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm leading-6 text-slate-600">
            PC에서 비밀번호 로그인 후 전송된 2단계 인증 요청을 여기에서 승인하거나 거절합니다.
            푸시 알림을 눌러 들어온 경우 해당 요청이 가장 위에 표시됩니다.
          </div>
          <Button variant="outline" onClick={() => void load()} disabled={loading} className="gap-2">
            {loading ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
            새로고침
          </Button>
          {message && <div className="rounded-lg border border-green-200 bg-green-50 p-3 text-sm text-green-700">{message}</div>}
          {error && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
          {sortedItems.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">
              대기 중인 로그인 승인 요청이 없습니다.
            </div>
          ) : (
            <div className="space-y-3">
              {sortedItems.map((item) => (
                <div
                  key={item.challengeToken}
                  className={`rounded-xl border bg-card p-4 shadow-sm ${
                    item.challengeToken === targetToken ? "border-blue-300 ring-2 ring-blue-100" : "border-slate-200"
                  }`}
                >
                  <div className="space-y-1 text-sm text-slate-600">
                    <div className="font-semibold text-slate-900">새 로그인 요청</div>
                    <div>요청 IP: {item.ipAddress ?? "확인 불가"}</div>
                    <div>만료 시각: {formatDate(item.expiresAt)}</div>
                    <div className="break-all">브라우저: {item.userAgent ?? "확인 불가"}</div>
                  </div>
                  <div className="mt-4 grid gap-2 sm:grid-cols-2">
                    <Button className="gap-2 bg-blue-600 text-white hover:bg-blue-700" onClick={() => void decide(item.challengeToken, true)}>
                      <CheckCircle2 className="size-4" />
                      승인
                    </Button>
                    <Button variant="outline" className="gap-2 text-red-600" onClick={() => void decide(item.challengeToken, false)}>
                      <XCircle className="size-4" />
                      거절
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
