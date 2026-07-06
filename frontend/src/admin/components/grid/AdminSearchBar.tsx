import { useEffect, useState, type ReactElement } from "react";
import { Search, SlidersHorizontal } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Input } from "@/app/components/ui/input";
import type { AdminFilterDef, AdminSearchTypeOption } from "./types";
import type { AdminAppliedSearch } from "./useAdminList";

/**
 * 관리자 공통 검색 바 — 컬럼 선택 + 키워드 + enum 필터 + 기간.
 *
 * 입력값은 내부 draft 로 들고 있다가 '검색'을 눌러야 적용된다(엔터 지원).
 * 좁은 화면에서는 필터/기간 영역을 '옵션' 버튼으로 접는다
 * (TT의 픽셀 브레이크포인트 물리 이동을 React 조건부 렌더로 대체).
 */
interface AdminSearchBarProps {
  searchTypes: AdminSearchTypeOption[];
  filterDefs?: AdminFilterDef[];
  showDateRange?: boolean;
  dateLabel?: string;
  applied: AdminAppliedSearch;
  onApply: (next: AdminAppliedSearch) => void;
  onReset: () => void;
}

export function AdminSearchBar({
  searchTypes,
  filterDefs = [],
  showDateRange = true,
  dateLabel = "기간",
  applied,
  onApply,
  onReset,
}: AdminSearchBarProps): ReactElement {
  const [draft, setDraft] = useState<AdminAppliedSearch>(applied);
  const [showOptions, setShowOptions] = useState(false);

  // 외부에서 초기화(resetSearch 등)되면 draft 도 동기화한다.
  useEffect(() => {
    setDraft(applied);
  }, [applied]);

  const submit = () => onApply(draft);
  const hasSecondary = filterDefs.length > 0 || showDateRange;

  return (
    <form
      className="space-y-3 rounded-lg border border-slate-200 bg-card p-4"
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
      role="search"
    >
      <div className="flex flex-wrap items-center gap-2">
        <select
          value={draft.searchType}
          onChange={(event) => setDraft((prev) => ({ ...prev, searchType: event.target.value }))}
          className="h-10 rounded-md border border-slate-200 bg-card px-3 text-sm"
          aria-label="검색 대상 컬럼"
        >
          {searchTypes.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <div className="relative min-w-[220px] flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <Input
            value={draft.keyword}
            onChange={(event) => setDraft((prev) => ({ ...prev, keyword: event.target.value }))}
            placeholder="검색어 입력"
            className="pl-9"
          />
        </div>
        <Button type="submit" className="bg-blue-600 text-white hover:bg-blue-700">
          검색
        </Button>
        <Button type="button" variant="outline" onClick={onReset}>
          초기화
        </Button>
        {hasSecondary && (
          <Button
            type="button"
            variant="ghost"
            className="md:hidden"
            onClick={() => setShowOptions((prev) => !prev)}
            aria-expanded={showOptions}
          >
            <SlidersHorizontal className="size-4" />
            옵션
          </Button>
        )}
      </div>

      {hasSecondary && (
        <div className={`${showOptions ? "flex" : "hidden"} flex-wrap items-end gap-3 md:flex`}>
          {filterDefs.map((filter) => (
            <label key={filter.key} className="flex flex-col gap-1 text-xs font-semibold text-slate-500">
              {filter.label}
              <select
                value={draft.filters[filter.key] ?? ""}
                onChange={(event) =>
                  setDraft((prev) => ({
                    ...prev,
                    filters: { ...prev.filters, [filter.key]: event.target.value },
                  }))
                }
                className="h-9 rounded-md border border-slate-200 bg-card px-2 text-sm font-normal text-slate-800"
              >
                <option value="">전체</option>
                {filter.options.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          ))}
          {showDateRange && (
            <label className="flex flex-col gap-1 text-xs font-semibold text-slate-500">
              {dateLabel}
              <span className="flex items-center gap-1 font-normal">
                <Input
                  type="date"
                  value={draft.dateFrom}
                  onChange={(event) => setDraft((prev) => ({ ...prev, dateFrom: event.target.value }))}
                  className="h-9 w-[150px]"
                />
                <span className="text-slate-400">~</span>
                <Input
                  type="date"
                  value={draft.dateTo}
                  onChange={(event) => setDraft((prev) => ({ ...prev, dateTo: event.target.value }))}
                  className="h-9 w-[150px]"
                />
              </span>
            </label>
          )}
        </div>
      )}
    </form>
  );
}
