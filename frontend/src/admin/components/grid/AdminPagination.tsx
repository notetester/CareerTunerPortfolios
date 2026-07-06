import type { ReactElement } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/app/components/ui/button";

/** 페이징 푸터 — 총건수 / 이전·다음 / 현재·총 페이지 / 표시 개수 선택. */
interface AdminPaginationProps {
  total: number;
  page: number;
  totalPages: number;
  size: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
  disabled?: boolean;
}

const PAGE_SIZES = [10, 20, 50, 100];

export function AdminPagination({
  total,
  page,
  totalPages,
  size,
  onPageChange,
  onSizeChange,
  disabled = false,
}: AdminPaginationProps): ReactElement {
  const lastPage = Math.max(totalPages, 1);
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-slate-600">
      <span>
        총 <b className="text-slate-900">{total.toLocaleString("ko-KR")}</b>건
      </span>
      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || page <= 1}
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeft className="size-4" />
          이전
        </Button>
        <span className="min-w-14 text-center tabular-nums">
          {Math.min(page, lastPage)} / {lastPage}
        </span>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || page >= lastPage}
          onClick={() => onPageChange(page + 1)}
        >
          다음
          <ChevronRight className="size-4" />
        </Button>
        <select
          value={size}
          onChange={(event) => onSizeChange(Number(event.target.value))}
          className="h-9 rounded-md border border-slate-200 bg-card px-2 text-sm"
          aria-label="페이지당 표시 개수"
          disabled={disabled}
        >
          {PAGE_SIZES.map((option) => (
            <option key={option} value={option}>
              {option}개씩
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
