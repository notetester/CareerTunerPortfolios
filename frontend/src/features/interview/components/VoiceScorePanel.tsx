import { Gauge } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import type { VoiceMetrics, VoiceScoreDetail } from "../types/interview";
import { getScoreColor } from "../types/interview";

const SCORE_LABELS: { key: keyof Omit<VoiceScoreDetail, "overall">; label: string; desc: string }[] = [
  { key: "pace", label: "말 속도", desc: "또박또박, 분당 250~400자 권장" },
  { key: "fluency", label: "전달력", desc: "군말(음·어·그…) 최소화" },
  { key: "stability", label: "톤 안정감", desc: "단조롭지도 불안정하지도 않은 억양" },
  { key: "confidence", label: "자신감", desc: "충분한 성량과 안정된 감정" },
  { key: "responsiveness", label: "반응 속도", desc: "질문 후 머뭇거림 없이 답변 시작" },
];

/**
 * 음성 점수 패널 — 종합 점수 + 항목별 점수 + 측정 지표 요약.
 * 음성 모의면접/아바타 화상 면접이 공용으로 쓴다.
 */
export function VoiceScorePanel({
  detail,
  metrics,
  title = "음성 분석 점수",
}: {
  detail: VoiceScoreDetail;
  metrics: VoiceMetrics;
  title?: string;
}) {
  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Gauge className="size-4 text-blue-600" />
          {title}
          <span className={`ml-auto text-2xl font-black ${getScoreColor(detail.overall)}`}>
            {detail.overall}
            <span className="text-sm font-bold text-slate-400">/100</span>
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-3">
          {SCORE_LABELS.map(({ key, label, desc }) => (
            <div key={key}>
              <div className="mb-1 flex items-baseline justify-between text-sm">
                <span className="font-semibold text-slate-700">{label}</span>
                <span className={`font-bold ${getScoreColor(detail[key])}`}>{detail[key]}</span>
              </div>
              <Progress value={detail[key]} className="h-2" />
              <p className="mt-0.5 text-[11px] text-slate-400">{desc}</p>
            </div>
          ))}
        </div>

        <div className="grid grid-cols-2 gap-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-600 sm:grid-cols-3">
          <Metric label="총 시간" value={`${Math.round(metrics.totalSec)}초`} />
          <Metric label="발화 시간" value={`${Math.round(metrics.speakingSec)}초`} />
          <Metric label="말 속도" value={metrics.speechRateSpm != null ? `${metrics.speechRateSpm}자/분` : "—"} />
          <Metric label="군말" value={`${metrics.fillerCount}회`} />
          <Metric label="평균 피치" value={metrics.avgPitchHz != null ? `${metrics.avgPitchHz}Hz` : "—"} />
          <Metric
            label="반응 지연"
            value={metrics.avgResponseLatencySec != null ? `${metrics.avgResponseLatencySec}초` : "—"}
          />
        </div>
      </CardContent>
    </Card>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-[10px] font-bold uppercase tracking-wide text-slate-400">{label}</div>
      <div className="font-semibold text-slate-700">{value}</div>
    </div>
  );
}
