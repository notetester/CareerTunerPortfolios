import { useState, type ReactNode } from "react";
import { AlertTriangle, Loader2, RefreshCw } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/app/components/ui/dropdown-menu";
import { cn } from "@/app/components/ui/utils";
import type { ProviderOption } from "../api/modelOptionsApi";

interface ProviderPickerButtonProps {
  /** 열릴 때 lazy 로 단계별(OCR·공고·기업) provider 선택지를 조회한다. selectable 만 클릭 가능. */
  loadOptions: () => Promise<ProviderOption[]>;
  onSelect: (provider: string) => void;
  /** 트리거 버튼 라벨. */
  label: string;
  /** 드롭다운 헤더(예: "재추출 OCR 모델 선택"·"재분석 모델 선택"). */
  menuLabel: string;
  /** selectable 이 0개일 때 안내(예: "선택 가능한 OCR 모델이 없습니다."). */
  emptyText: string;
  /** 실행 중이면 아이콘 스핀 + 트리거 비활성. */
  pending?: boolean;
  disabled?: boolean;
  size?: "sm" | "default";
  variant?: "default" | "outline" | "ghost" | "destructive";
  className?: string;
  icon?: ReactNode;
}

/**
 * 단계(OCR/공고분석/기업분석) 무관 provider 선택 드롭다운. model-options 를 열릴 때 lazy 조회하고
 * <b>selectable provider 만</b> 클릭 가능하다. 선택지 0개/조회 실패 시 실행하지 않고 원인을 보여준다.
 * OCR 재추출(OcrRetryButton)·분석 재분석(AnalysisReanalyzeButton)이 공유한다.
 */
export function ProviderPickerButton({
  loadOptions,
  onSelect,
  label,
  menuLabel,
  emptyText,
  pending = false,
  disabled = false,
  size = "sm",
  variant = "outline",
  className,
  icon,
}: ProviderPickerButtonProps) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [options, setOptions] = useState<ProviderOption[] | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setOptions(await loadOptions());
    } catch (err) {
      setError(err instanceof Error ? err.message : "모델 목록을 불러오지 못했습니다.");
      setOptions(null);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    if (next && options === null && !loading) {
      void load();
    }
  };

  const selectable = (options ?? []).filter((option) => option.selectable);
  const unavailable = (options ?? []).filter((option) => !option.selectable);

  return (
    <DropdownMenu open={open} onOpenChange={handleOpenChange}>
      <DropdownMenuTrigger asChild>
        <Button type="button" size={size} variant={variant} disabled={disabled || pending} className={className}>
          {icon ?? <RefreshCw className={cn("size-3.5", pending && "animate-spin")} />}
          {label}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-64">
        <DropdownMenuLabel>{menuLabel}</DropdownMenuLabel>
        <DropdownMenuSeparator />

        {loading && (
          <div className="flex items-center gap-2 px-2 py-2 text-sm text-slate-500">
            <Loader2 className="size-4 animate-spin" />
            모델 확인 중…
          </div>
        )}

        {!loading && error && (
          <div className="px-2 py-2 text-sm text-red-600">
            <div className="flex items-center gap-1.5 font-medium">
              <AlertTriangle className="size-4" />
              불러오지 못했습니다
            </div>
            <p className="mt-1 text-xs text-red-500">{error}</p>
            <button
              type="button"
              className="mt-2 text-xs font-semibold text-blue-600 hover:underline"
              onClick={() => void load()}
            >
              다시 시도
            </button>
          </div>
        )}

        {!loading && !error && options !== null && selectable.length === 0 && (
          <div className="px-2 py-2 text-sm text-slate-500">
            {emptyText}
            {unavailable.length > 0 && (
              <ul className="mt-1 space-y-0.5 text-xs text-slate-400">
                {unavailable.map((option) => (
                  <li key={option.provider}>
                    {option.displayName}
                    {option.reason ? ` — ${option.reason}` : ""}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {!loading && !error &&
          selectable.map((option) => (
            <DropdownMenuItem
              key={option.provider}
              className="flex-col items-start gap-0.5"
              onSelect={() => {
                setOpen(false);
                onSelect(option.provider);
              }}
            >
              <span className="font-medium">{option.displayName}</span>
              {option.actualModel && <span className="text-xs text-slate-500">{option.actualModel}</span>}
            </DropdownMenuItem>
          ))}

        {!loading && !error && selectable.length > 0 && unavailable.length > 0 && (
          <>
            <DropdownMenuSeparator />
            {unavailable.map((option) => (
              <DropdownMenuItem key={option.provider} disabled className="flex-col items-start gap-0.5">
                <span>{option.displayName}</span>
                {option.reason && <span className="text-xs text-slate-400">{option.reason}</span>}
              </DropdownMenuItem>
            ))}
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
