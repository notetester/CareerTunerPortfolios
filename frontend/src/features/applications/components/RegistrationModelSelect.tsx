import { Loader2 } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import type { StageOptions } from "../api/modelOptionsApi";

/** "자동(기본 체인)" 선택 sentinel — 이 값이면 요청에 provider 를 넣지 않아 백엔드 기본 체인을 쓴다. */
export const AUTO_PROVIDER = "AUTO";

interface RegistrationModelSelectProps {
  label: string;
  hint?: string;
  /** 단계별(공고분석/기업분석/OCR) 선택지. 로딩 전/조회 실패면 null(자동만 노출). */
  stage: StageOptions | null;
  loading: boolean;
  /** 현재 선택값 — {@link AUTO_PROVIDER} 또는 provider 이름. */
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}

/**
 * 신규 등록 시 초기 실행 모델을 고르는 지속 선택 컨트롤. model-options 의 단계별 선택지를 그대로 보여주고,
 * <b>selectable provider 만 선택 가능</b>하며 불가한 provider 는 사유와 함께 비활성으로 노출한다.
 * 기본값은 "자동" — 이때 요청에 provider 를 넣지 않아 백엔드 기본 체인을 그대로 쓴다(provenance NULL).
 * 즉시 실행하는 {@link ../components/ProviderPickerButton} 와 달리 폼 제출 전까지 선택만 유지한다.
 */
export function RegistrationModelSelect({
  label,
  hint,
  stage,
  loading,
  value,
  onChange,
  disabled = false,
}: RegistrationModelSelectProps) {
  const options = stage?.options ?? [];

  return (
    <label className="grid gap-2 text-sm font-semibold text-slate-700">
      {label}
      <Select value={value} onValueChange={onChange} disabled={disabled || loading}>
        <SelectTrigger>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={AUTO_PROVIDER}>자동 (추천 기본값)</SelectItem>
          {options.map((option) => (
            <SelectItem key={option.provider} value={option.provider} disabled={!option.selectable}>
              {option.selectable
                ? option.actualModel
                  ? `${option.displayName} · ${option.actualModel}`
                  : option.displayName
                : `${option.displayName} — ${option.reason ?? "사용 불가"}`}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {loading ? (
        <span className="flex items-center gap-1.5 text-xs font-normal text-slate-400">
          <Loader2 className="size-3 animate-spin" />
          모델 확인 중…
        </span>
      ) : (
        hint && <span className="text-xs font-normal leading-5 text-slate-500">{hint}</span>
      )}
    </label>
  );
}
