import { FileClock, LoaderCircle, RefreshCw, Trash2 } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import type { CorrectionResponse } from "../types/correction";

interface CorrectionHistoryListProps {
  items: CorrectionResponse[];
  selectedId: number | null;
  loading: boolean;
  loadingId: number | null;
  deletingId: number | null;
  error: string | null;
  onSelect: (id: number) => void;
  onRetry: () => void;
  onDelete: (id: number) => void;
}

export function CorrectionHistoryList({
  items,
  selectedId,
  loading,
  loadingId,
  deletingId,
  error,
  onSelect,
  onRetry,
  onDelete,
}: CorrectionHistoryListProps) {
  if (loading) {
    return (
      <div className="flex min-h-32 items-center justify-center text-sm text-slate-500">
        <LoaderCircle className="mr-2 size-4 animate-spin" />
        기록을 불러오는 중
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-3 py-4 text-center">
        <p className="text-sm leading-5 text-red-600">{error}</p>
        <Button type="button" size="sm" variant="outline" onClick={onRetry}>
          <RefreshCw className="size-4" />
          다시 불러오기
        </Button>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex min-h-32 flex-col items-center justify-center px-3 text-center text-sm text-slate-400">
        <FileClock className="mb-2 size-6" />
        아직 저장된 첨삭 기록이 없습니다.
      </div>
    );
  }

  return (
    <div className="max-h-[420px] space-y-2 overflow-y-auto pr-1">
      {items.map((item) => {
        const active = selectedId === item.id;
        const detailLoading = loadingId === item.id;
        return (
          <div
            key={item.id}
            className={`flex items-start gap-1 rounded-lg border p-1 transition-colors ${
              active
                ? "border-blue-300 bg-blue-50"
                : "border-slate-200 bg-card hover:border-slate-300 hover:bg-slate-50"
            }`}
          >
            <button
              type="button"
              aria-pressed={active}
              disabled={detailLoading || deletingId === item.id}
              onClick={() => onSelect(item.id)}
              className="min-w-0 flex-1 rounded-md p-2 text-left"
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-semibold text-slate-500">{formatDate(item.createdAt)}</span>
                {detailLoading && <LoaderCircle className="size-3.5 animate-spin text-blue-600" />}
              </div>
              <p className="mt-1 line-clamp-2 break-words text-sm font-semibold leading-5 text-slate-800">
                {item.summary || item.improvedText || item.originalText}
              </p>
            </button>
            <Button
              type="button"
              size="icon"
              variant="ghost"
              aria-label="첨삭 기록 삭제"
              disabled={deletingId !== null}
              onClick={() => {
                if (window.confirm("이 첨삭 기록을 삭제하시겠습니까?")) onDelete(item.id);
              }}
              className="mt-1 size-9 shrink-0 text-slate-500 hover:text-red-600"
            >
              {deletingId === item.id ? <LoaderCircle className="size-4 animate-spin" /> : <Trash2 className="size-4" />}
            </Button>
          </div>
        );
      })}
    </div>
  );
}

function formatDate(value: string | null) {
  if (!value) return "생성 시각 없음";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "생성 시각 없음";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}
