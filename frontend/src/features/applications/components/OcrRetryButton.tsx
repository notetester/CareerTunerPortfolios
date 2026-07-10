import { useState } from "react";
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
import type { ApplicationSourceType } from "../types/applicationCase";
import { getModelOptions, type ProviderOption } from "../api/modelOptionsApi";

/** OCR 재추출은 파일 공고(PDF/IMAGE)에만 적용된다 — URL/TEXT/MANUAL 은 버튼 자체를 노출하지 않는다. */
export function isOcrRetrySource(sourceType: ApplicationSourceType): boolean {
  return sourceType === "PDF" || sourceType === "IMAGE";
}

interface OcrRetryButtonProps {
  /** 최신 추출의 sourceType(지원 건의 일반 sourceType 이 아니라 실제 추출 기준). */
  sourceType: ApplicationSourceType;
  retrying?: boolean;
  disabled?: boolean;
  onRetry: (ocrProvider: string) => void;
  label?: string;
  size?: "sm" | "default";
  variant?: "default" | "outline" | "ghost" | "destructive";
  className?: string;
}

/**
 * 실패한 공고 추출을 사용자가 고른 OCR 모델로 재추출한다(strict: 단일 provider, 교차 폴백 없음).
 * 열릴 때 model-options 를 lazy 조회하고, <b>selectable provider 만</b> 클릭 가능하다.
 * 선택 가능한 모델이 없거나 조회에 실패하면 재추출을 실행하지 않고 원인을 보여준다.
 */
export function OcrRetryButton({
  sourceType,
  retrying = false,
  disabled = false,
  onRetry,
  label = "다시 추출",
  size = "sm",
  variant = "outline",
  className,
}: OcrRetryButtonProps) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [options, setOptions] = useState<ProviderOption[] | null>(null);

  if (!isOcrRetrySource(sourceType)) {
    return null;
  }

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getModelOptions(sourceType);
      setOptions(result.ocr?.options ?? []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "OCR 모델 목록을 불러오지 못했습니다.");
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
        <Button type="button" size={size} variant={variant} disabled={disabled || retrying} className={className}>
          <RefreshCw className={cn("size-3.5", retrying && "animate-spin")} />
          {label}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-64">
        <DropdownMenuLabel>재추출 OCR 모델 선택</DropdownMenuLabel>
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
            선택 가능한 OCR 모델이 없습니다.
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
                onRetry(option.provider);
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
