import { useState } from "react";
import { ArrowLeft, Mic, Radio } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card } from "@/app/components/ui/card";
import type { InterviewSession } from "../types/interview";
import { RealtimeInterviewTab } from "./RealtimeInterviewTab";
import { LocalVoiceInterviewTab } from "./LocalVoiceInterviewTab";

type Mode = "premium" | "basic";

/**
 * 음성 모의면접 진입 — 방식(프리미엄/베이직) 선택 후 해당 면접으로.
 * 프리미엄 = 실시간 AI 음성 대화(OpenAI Realtime). 베이직 = 녹음형 자체 AI(API 0).
 * 요금제 게이팅(plan)은 E 요금제 DB 생성 후 — 현재는 사용자가 직접 선택(placeholder).
 */
export function VoiceInterviewTab({ session }: { session: InterviewSession | null }) {
  const [mode, setMode] = useState<Mode | null>(null);

  if (!session) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-white p-10 text-center text-sm text-slate-400">
        "면접 모드 선택" 탭에서 지원 건과 모드를 고르고 면접을 시작하면 음성 모의면접을 진행할 수 있습니다.
      </div>
    );
  }

  if (mode) {
    return (
      <div className="space-y-3">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setMode(null)}
          className="gap-1.5 text-slate-500"
        >
          <ArrowLeft className="size-4" /> 면접 방식 다시 선택
        </Button>
        {mode === "premium" ? (
          <RealtimeInterviewTab session={session} />
        ) : (
          <LocalVoiceInterviewTab session={session} />
        )}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-500">
        음성 모의면접 방식을 선택하세요. 두 방식 모두 준비된 질문으로 진행됩니다.
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <Card
          onClick={() => setMode("premium")}
          className="cursor-pointer border-slate-200 p-5 transition hover:border-rose-300 hover:shadow"
        >
          <div className="mb-2 flex items-center gap-2">
            <Radio className="size-5 text-rose-600" />
            <span className="font-semibold text-slate-800">실시간 AI 음성 면접</span>
            <Badge className="bg-rose-100 text-rose-700">프리미엄</Badge>
          </div>
          <p className="text-sm text-slate-500">
            AI 면접관이 <b>음성으로 실시간 대화</b>합니다. 말로 묻고 끊김없이 주고받는 진짜 면접 경험. (OpenAI 기반)
          </p>
        </Card>
        <Card
          onClick={() => setMode("basic")}
          className="cursor-pointer border-slate-200 p-5 transition hover:border-emerald-300 hover:shadow"
        >
          <div className="mb-2 flex items-center gap-2">
            <Mic className="size-5 text-emerald-600" />
            <span className="font-semibold text-slate-800">자체 AI 음성 면접</span>
            <Badge className="bg-emerald-100 text-emerald-700">베이직 · 무료</Badge>
          </div>
          <p className="text-sm text-slate-500">
            면접관이 질문을 읽어주면 <b>자동으로 녹음</b>됩니다. 자체 AI가 말 속도·톤·내용을 채점합니다. 외부 API 없이(무료) 동작합니다.
          </p>
        </Card>
      </div>
    </div>
  );
}
