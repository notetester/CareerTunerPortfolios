// 설정 > 차단 관리 탭 — "간편(수신 범위) → 고급(매트릭스) → 개별(차단 목록)" 3단 구조 (설계문서 §4).
// 정책 문서는 이 컨테이너가 한 번 로드해 두 정책 섹션이 공유하고, 차단 목록은 자체 로드한다.
import { useCallback, useEffect, useState } from "react";
import { RefreshCw, ShieldBan } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { getPrivacyPolicy } from "../api/privacyApi";
import type { PrivacyPolicyResponse } from "../types";
import { AdvancedPolicyMatrix } from "./AdvancedPolicyMatrix";
import { BlockLists } from "./BlockLists";
import { SimpleReceiveSettings } from "./SimpleReceiveSettings";

export function PrivacySettings() {
  const [policy, setPolicy] = useState<PrivacyPolicyResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPolicy(await getPrivacyPolicy());
    } catch (err) {
      setError(err instanceof Error ? err.message : "차단 정책을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2 text-sm text-slate-600">
          <ShieldBan className="size-4 text-red-600" />
          누구로부터 무엇을 받을지, 무엇을 볼지 직접 정합니다. 차단은 상대에게 알려지지 않아요.
        </div>
        <Button type="button" size="sm" variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      )}

      {policy ? (
        <>
          <SimpleReceiveSettings policy={policy} onPolicyChange={setPolicy} onError={setError} />
          <AdvancedPolicyMatrix policy={policy} onPolicyChange={setPolicy} onError={setError} />
        </>
      ) : (
        !error && <div className="rounded-lg border border-slate-200 bg-card p-6 text-sm text-slate-500">차단 정책을 불러오는 중입니다.</div>
      )}

      <BlockLists blockedAccountEffective={policy?.effective.blockedAccount ?? {}} onError={setError} />
    </div>
  );
}
