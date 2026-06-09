import { Clock, Play } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";

const PRACTICE_INFO = [
  { label: "면접 시간", value: "30분", desc: "실제 면접 흐름과 유사한 제한 시간" },
  { label: "질문 구성", value: "8문항", desc: "자기소개, 직무, 인성, 꼬리질문 혼합" },
  { label: "진행 방식", value: "랜덤", desc: "공고와 프로필 기반으로 즉시 출제" },
];

/** 실전 모의면접 안내. (실제 진행 흐름은 백엔드 세션 연동 후 확장) */
export function PracticeTab({ disabled }: { disabled: boolean }) {
  return (
    <div className="space-y-5">
      <Card className="border border-slate-200 bg-white">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Clock className="size-4 text-blue-600" />
            실전 모의면접
          </CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-3">
          {PRACTICE_INFO.map((item) => (
            <div key={item.label} className="rounded-xl bg-slate-50 p-4">
              <div className="text-xs text-slate-500">{item.label}</div>
              <div className="mt-1 text-2xl font-black text-slate-900">{item.value}</div>
              <div className="mt-2 text-xs text-slate-500">{item.desc}</div>
            </div>
          ))}
        </CardContent>
      </Card>
      <Button disabled={disabled} className="bg-gradient-to-r from-blue-600 to-indigo-600">
        <Play className="mr-2 size-4" />
        실전 모의면접 시작
      </Button>
      {disabled && <p className="text-xs text-slate-400">먼저 "면접 모드 선택"에서 모드를 골라주세요.</p>}
    </div>
  );
}
