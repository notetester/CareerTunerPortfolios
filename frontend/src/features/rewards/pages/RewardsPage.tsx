import { useCallback, useEffect, useState } from "react";
import { Award, Coins, Gift, Ticket, TrendingUp } from "lucide-react";

import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Badge } from "@/app/components/ui/badge";
import { Progress } from "@/app/components/ui/progress";
import {
  getMyCoupons,
  getMyReward,
  redeemCoupon,
  type MyCoupon,
  type MyReward,
} from "../api/rewardApi";

const EVENT_LABEL: Record<string, string> = {
  COMMUNITY_POST_CREATE: "글 작성",
  COMMUNITY_COMMENT_CREATE: "댓글 작성",
  APPLICATION_CASE_READY: "지원 건 분석 완료",
  DAILY_LOGIN: "일일 로그인",
  CREDIT_PURCHASE: "크레딧 구매 페이백",
  LEVEL_UP: "레벨업 보상",
  COUPON: "쿠폰 사용",
};

function fmt(value: string): string {
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString("ko-KR");
}

export function RewardsPage() {
  const [reward, setReward] = useState<MyReward | null>(null);
  const [coupons, setCoupons] = useState<MyCoupon[]>([]);
  const [code, setCode] = useState("");
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const [r, c] = await Promise.allSettled([getMyReward(), getMyCoupons()]);
    if (r.status === "fulfilled") setReward(r.value);
    if (c.status === "fulfilled") setCoupons(c.value);
  }, []);
  useEffect(() => { void load(); }, [load]);

  const doRedeem = async (redeemCode: string) => {
    if (!redeemCode.trim()) return;
    setBusy(true);
    setMsg(null);
    try {
      const res = await redeemCoupon(redeemCode.trim().toUpperCase());
      setMsg({ ok: true, text: res.message });
      setCode("");
      await load();
    } catch (e) {
      setMsg({ ok: false, text: e instanceof Error ? e.message : "쿠폰 사용에 실패했습니다." });
    } finally {
      setBusy(false);
    }
  };

  const pct = reward && reward.nextLevel && reward.pointToNextLevel != null
    ? Math.max(0, Math.min(100, Math.round(100 * (1 - reward.pointToNextLevel / Math.max(1, reward.pointToNextLevel + reward.activityPoint)))))
    : 100;

  return (
    <div className="mx-auto max-w-3xl px-4 py-6">
      <div className="mb-5 flex items-center gap-2">
        <Gift className="h-6 w-6 text-indigo-600" />
        <h1 className="text-xl font-bold text-slate-900">내 리워드 · 레벨</h1>
      </div>

      {/* 레벨 카드 */}
      <Card className="mb-4">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Award className="h-5 w-5 text-amber-500" /> 활동 레벨
          </CardTitle>
        </CardHeader>
        <CardContent>
          {reward ? (
            <>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-2xl font-bold text-slate-900">Lv.{reward.level} <span className="text-indigo-600">{reward.levelName}</span></div>
                  <div className="mt-1 flex items-center gap-1 text-sm text-slate-500">
                    <TrendingUp className="h-4 w-4" /> 누적 활동 포인트 {reward.activityPoint.toLocaleString("ko-KR")}p
                  </div>
                </div>
                <div className="flex items-center gap-1 rounded-lg bg-sky-50 px-3 py-2 text-sky-700">
                  <Coins className="h-4 w-4" /> 크레딧 {reward.credit.toLocaleString("ko-KR")}
                </div>
              </div>
              <div className="mt-4">
                <Progress value={pct} className="h-2" />
                <div className="mt-1 text-xs text-slate-500">
                  {reward.nextLevel != null && reward.pointToNextLevel != null
                    ? `다음 레벨 Lv.${reward.nextLevel} ${reward.nextLevelName ?? ""}까지 ${reward.pointToNextLevel.toLocaleString("ko-KR")}p`
                    : "최고 레벨에 도달했습니다."}
                </div>
              </div>
            </>
          ) : (
            <div className="py-6 text-center text-sm text-slate-400">불러오는 중…</div>
          )}
        </CardContent>
      </Card>

      {/* 쿠폰 */}
      <Card className="mb-4">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Ticket className="h-5 w-5 text-rose-500" /> 내 쿠폰
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="mb-3 flex flex-wrap items-center gap-2">
            <input
              className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm"
              placeholder="쿠폰 코드 입력 (예: WELCOME10)"
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
            <Button onClick={() => void doRedeem(code)} disabled={busy || !code.trim()}>사용</Button>
          </div>
          {msg && (
            <div className={`mb-3 rounded-lg px-3 py-2 text-sm ${msg.ok ? "bg-emerald-50 text-emerald-700" : "bg-rose-50 text-rose-600"}`}>
              {msg.text}
            </div>
          )}
          <div className="space-y-2">
            {coupons.length === 0 && <div className="py-4 text-center text-sm text-slate-400">보유한 쿠폰이 없습니다.</div>}
            {coupons.map((c) => (
              <div key={c.id} className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-sm font-semibold text-slate-800">{c.code}</span>
                    <Badge variant="secondary">{c.discountType === "CREDIT" ? `크레딧 ${c.discountValue}` : c.discountType === "PERCENT" ? `${c.discountValue}% 할인` : `${c.discountValue.toLocaleString("ko-KR")}원 할인`}</Badge>
                    {c.status !== "ISSUED" && <Badge variant="outline">{c.status === "USED" ? "사용됨" : "만료"}</Badge>}
                  </div>
                  <div className="text-xs text-slate-400">{c.name}</div>
                </div>
                {c.status === "ISSUED" && c.discountType === "CREDIT" && (
                  <Button size="sm" variant="outline" onClick={() => void doRedeem(c.code)} disabled={busy}>크레딧으로 사용</Button>
                )}
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* 적립 이력 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">최근 적립 이력</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-1">
            {(!reward || reward.recentHistory.length === 0) && (
              <div className="py-4 text-center text-sm text-slate-400">아직 적립 내역이 없습니다.</div>
            )}
            {reward?.recentHistory.map((h) => (
              <div key={h.id} className="flex items-center justify-between border-b border-slate-100 py-2 text-sm last:border-0">
                <div>
                  <div className="font-medium text-slate-700">{EVENT_LABEL[h.eventCode] ?? h.eventCode}</div>
                  <div className="text-xs text-slate-400">{fmt(h.createdAt)}</div>
                </div>
                <div className="flex items-center gap-2 text-xs">
                  {h.pointDelta > 0 && <span className="font-semibold text-emerald-600">+{h.pointDelta}p</span>}
                  {h.creditDelta > 0 && <span className="font-semibold text-sky-600">+{h.creditDelta} 크레딧</span>}
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
