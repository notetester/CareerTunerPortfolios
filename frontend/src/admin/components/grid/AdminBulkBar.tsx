import type { ReactElement, ReactNode } from "react";
import { X } from "lucide-react";
import { Button } from "@/app/components/ui/button";

/**
 * 일괄 작업 바 — 고정 슬롯(선택 0건이어도 자리를 유지해 레이아웃 점프 방지),
 * 선택 카운트는 aria-live 로 스크린리더에 안내한다(TT UX 규약).
 */
interface AdminBulkBarProps {
  count: number;
  onClear: () => void;
  /** 선택이 있을 때 노출되는 일괄 액션 컨트롤들. */
  children?: ReactNode;
}

export function AdminBulkBar({ count, onClear, children }: AdminBulkBarProps): ReactElement {
  const active = count > 0;
  return (
    <div
      className={`flex min-h-11 flex-wrap items-center gap-2 rounded-lg border px-3 py-1.5 transition-colors ${
        active ? "border-blue-200 bg-blue-50" : "border-dashed border-slate-200 bg-slate-50/60"
      }`}
    >
      <span aria-live="polite" className={`text-sm font-semibold ${active ? "text-blue-700" : "text-slate-400"}`}>
        {active ? `${count}건 선택됨` : "선택된 항목 없음"}
      </span>
      {active && (
        <>
          <Button type="button" variant="ghost" size="sm" className="h-7 px-2 text-slate-500" onClick={onClear}>
            <X className="size-3.5" />
            선택 해제
          </Button>
          <div className="flex flex-wrap items-center gap-2">{children}</div>
        </>
      )}
    </div>
  );
}
