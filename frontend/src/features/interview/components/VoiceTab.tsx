import { Mic } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Badge } from "@/app/components/ui/badge";

// 1차: UI 골격만. 실제 녹음/STT/음성지표 분석은 2차에서 연동한다.
const VOICE_METRICS = ["말 속도", "침묵 구간", "반복 표현", "답변 길이"];

export function VoiceTab() {
  return (
    <Card className="border border-slate-200 bg-white">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Mic className="size-4 text-purple-600" />
          음성 면접
          <Badge className="bg-slate-100 text-slate-600">준비 중 (2차)</Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-slate-500">
          마이크로 답변하면 음성을 텍스트로 변환해 평가하고, 아래 지표를 분석합니다. (녹음·분석 연동 예정)
        </p>
        <div className="grid gap-3 md:grid-cols-2">
          {VOICE_METRICS.map((metric) => (
            <div key={metric} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm font-semibold text-slate-800">{metric}</div>
              <div className="mt-1 text-xs text-slate-500">녹음 기반 분석 지표가 이 영역에 표시됩니다</div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
