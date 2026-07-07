import { useCallback, useEffect, useState } from "react";
import { AtSign, KeyRound, Link2, Phone, ShieldCheck } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { getAccountInfo, setLoginId, setPhone } from "../api/nicknameProfileApi";
import { PROVIDER_LABELS, type AccountInfo } from "../types/nicknameProfile";

/**
 * 계정 정보 카드 — 로그인 아이디(최초 1회 설정), 전화번호, 연결된 소셜 계정.
 *
 * 아이디는 설정 후 변경 불가, 전화번호는 언제든 변경 가능(인증은 선택적·스텁).
 */
export function AccountInfoCard() {
  const [info, setInfo] = useState<AccountInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [loginIdDraft, setLoginIdDraft] = useState("");
  const [phoneDraft, setPhoneDraft] = useState("");
  const [savingLoginId, setSavingLoginId] = useState(false);
  const [savingPhone, setSavingPhone] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const next = await getAccountInfo();
      setInfo(next);
      setPhoneDraft(next.phone ?? "");
    } catch (e) {
      setError(e instanceof Error ? e.message : "계정 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submitLoginId = async () => {
    const value = loginIdDraft.trim().toLowerCase();
    if (!value) {
      setError("아이디를 입력해 주세요.");
      return;
    }
    setSavingLoginId(true);
    setError(null);
    setMessage(null);
    try {
      setInfo(await setLoginId(value));
      setLoginIdDraft("");
      setMessage("로그인 아이디를 설정했습니다.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "아이디 설정에 실패했습니다.");
    } finally {
      setSavingLoginId(false);
    }
  };

  const submitPhone = async () => {
    const value = phoneDraft.trim();
    if (!value) {
      setError("전화번호를 입력해 주세요.");
      return;
    }
    setSavingPhone(true);
    setError(null);
    setMessage(null);
    try {
      setInfo(await setPhone(value));
      setMessage("전화번호를 저장했습니다.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "전화번호 저장에 실패했습니다.");
    } finally {
      setSavingPhone(false);
    }
  };

  if (loading) {
    return (
      <Card className="border-slate-200">
        <CardContent className="py-8 text-center text-sm text-slate-500">계정 정보를 불러오는 중...</CardContent>
      </Card>
    );
  }

  return (
    <Card className="border-slate-200">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <ShieldCheck className="size-5 text-blue-600" />
          계정 정보
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        {message && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}

        <div className="grid gap-1">
          <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <AtSign className="size-3.5" />
            이메일
          </span>
          <span className="text-sm text-slate-800">{info?.email}</span>
        </div>

        {/* 로그인 아이디 — 최초 1회 설정 */}
        <div className="space-y-2">
          <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <KeyRound className="size-3.5" />
            로그인 아이디
          </span>
          {info?.loginIdSet ? (
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-slate-800">{info.loginId}</span>
              <Badge className="bg-slate-100 text-slate-600">변경 불가</Badge>
            </div>
          ) : (
            <div className="flex flex-wrap gap-2">
              <Input
                className="max-w-xs"
                value={loginIdDraft}
                onChange={(e) => setLoginIdDraft(e.target.value)}
                placeholder="영문 소문자/숫자/밑줄 4~50자"
              />
              <Button size="sm" className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => void submitLoginId()} disabled={savingLoginId}>
                {savingLoginId ? "설정 중..." : "아이디 설정"}
              </Button>
              <p className="w-full text-xs text-amber-600">아이디는 한 번 설정하면 변경할 수 없습니다.</p>
            </div>
          )}
        </div>

        {/* 전화번호 */}
        <div className="space-y-2">
          <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <Phone className="size-3.5" />
            전화번호
          </span>
          <div className="flex flex-wrap items-center gap-2">
            <Input
              className="max-w-xs"
              value={phoneDraft}
              onChange={(e) => setPhoneDraft(e.target.value)}
              placeholder="010-1234-5678"
            />
            <Button size="sm" variant="outline" onClick={() => void submitPhone()} disabled={savingPhone}>
              {savingPhone ? "저장 중..." : "저장"}
            </Button>
            {info?.phone && (
              <Badge className={info.phoneVerified ? "bg-green-100 text-green-700" : "bg-slate-100 text-slate-600"}>
                {info.phoneVerified ? "인증됨" : "미인증"}
              </Badge>
            )}
          </div>
        </div>

        {/* 연결된 소셜 계정 */}
        <div className="space-y-2">
          <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <Link2 className="size-3.5" />
            연결된 계정
          </span>
          {info && info.linkedProviders.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {info.linkedProviders.map((provider) => (
                <Badge key={provider} className="bg-blue-50 text-blue-700">
                  {PROVIDER_LABELS[provider] ?? provider}
                </Badge>
              ))}
            </div>
          ) : (
            <p className="text-sm text-slate-500">연결된 소셜 계정이 없습니다.</p>
          )}
          <p className="text-xs text-slate-400">
            소셜 계정 연결/해제는 로그인 설정에서 관리합니다. 남는 로그인 수단이 없으면 해제가 제한됩니다.
          </p>
        </div>
      </CardContent>
    </Card>
  );
}
