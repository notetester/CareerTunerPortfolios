import { Cpu } from "lucide-react";

/** 사용자가 각 AI 기능 사용 시 명시 선택하는 모델. 백엔드 RequestedAiModel 과 값 집합 일치. */
export type AiModelChoice = "AUTO" | "CAREERTUNER" | "CLAUDE" | "OPENAI";

const OPTIONS: { value: AiModelChoice; label: string }[] = [
  { value: "AUTO", label: "자동 (추천)" },
  { value: "CAREERTUNER", label: "CareerTuner 자체 모델" },
  { value: "CLAUDE", label: "Claude Haiku" },
  { value: "OPENAI", label: "OpenAI GPT" },
];

/**
 * 재사용 AI 모델 선택기. 기본값 AUTO('자동(추천)') — 선택은 시작 provider 만 바꾸고, 실패 시 하위 폴백으로
 * 화면이 깨지지 않는다. 각 AI 액션 버튼 옆에 인라인으로 둔다.
 */
export function ModelPicker({
  value,
  onChange,
  disabled,
  className,
}: {
  value: AiModelChoice;
  onChange: (value: AiModelChoice) => void;
  disabled?: boolean;
  className?: string;
}) {
  return (
    <label className={`inline-flex items-center gap-1.5 text-xs text-muted-foreground ${className ?? ""}`}>
      <Cpu className="size-3.5 text-muted-foreground" />
      <span className="sr-only">AI 모델 선택</span>
      <select
        aria-label="AI 모델 선택"
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value as AiModelChoice)}
        className="rounded-md border border-border bg-card px-2 py-1 text-xs font-medium text-foreground disabled:opacity-50"
      >
        {OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
