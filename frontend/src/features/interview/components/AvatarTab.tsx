import { Video } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";

// 1차: UI 골격만. 실시간 아바타 면접관(TTS·입모양·표정·웹캠 분석)은 3차 최종 목표.
const AVATAR_FEATURES = ["시선 처리", "표정 피드백", "꼬리 질문"];

export function AvatarTab() {
  return (
    <Card className="overflow-hidden border border-slate-200 bg-white">
      <div className="grid lg:grid-cols-[minmax(0,1fr)_360px]">
        <CardContent className="space-y-4 p-6">
          <Badge className="bg-purple-100 text-purple-700">프리미엄 · 3차 예정</Badge>
          <h2 className="text-xl font-black text-slate-900">아바타 면접관</h2>
          <p className="text-sm leading-relaxed text-slate-600">
            AI 면접관이 실제 음성·입모양·표정·제스처로 질문하고, 웹캠·마이크로 받은 답변의 말투·표정·적합도를
            분석하는 실시간 면접 시뮬레이션입니다. (최종 단계에서 연동)
          </p>
          <div className="grid gap-3 sm:grid-cols-3">
            {AVATAR_FEATURES.map((item) => (
              <div key={item} className="rounded-xl bg-purple-50 p-3 text-sm font-semibold text-purple-800">
                {item}
              </div>
            ))}
          </div>
          <Button variant="outline" disabled className="border-purple-300 text-purple-700">
            아바타 면접 설정 (준비 중)
          </Button>
        </CardContent>
        <div className="flex min-h-64 items-center justify-center bg-gradient-to-br from-slate-900 via-indigo-950 to-blue-950 p-6">
          <div className="rounded-full border-4 border-blue-300 bg-white/10 p-8 text-center text-white shadow-2xl">
            <Video className="mx-auto size-12" />
            <div className="mt-3 text-sm font-semibold">AI 면접관 대기 중</div>
          </div>
        </div>
      </div>
    </Card>
  );
}
