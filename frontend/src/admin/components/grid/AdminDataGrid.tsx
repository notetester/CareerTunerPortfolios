import { useEffect, useRef, type ReactElement } from "react";
import { RefreshCw } from "lucide-react";
import type { AdminColumn, AdminSortDir } from "./types";
import type { AdminSelectionApi } from "./useAdminList";

/**
 * 관리자 공통 데이터 그리드.
 *
 * 정렬 UX 규약(TT 이식): 단일 컬럼 정렬, 활성 컬럼에만 ▲(빨강, ASC)/▼(파랑, DESC),
 * 비활성 컬럼에는 화살표를 표시하지 않는다.
 */
interface AdminDataGridProps<T> {
  columns: AdminColumn<T>[];
  rows: T[];
  rowKey: (row: T) => number;
  loading?: boolean;
  emptyText?: string;
  sortBy?: string;
  sortDir?: AdminSortDir;
  onToggleSort?: (key: string) => void;
  selectable?: boolean;
  selection?: AdminSelectionApi;
  onRowClick?: (row: T) => void;
}

export function AdminDataGrid<T>({
  columns,
  rows,
  rowKey,
  loading = false,
  emptyText = "현재 조건에 맞는 항목이 없습니다.",
  sortBy,
  sortDir,
  onToggleSort,
  selectable = false,
  selection,
  onRowClick,
}: AdminDataGridProps<T>): ReactElement {
  const visibleIds = rows.map(rowKey);
  const allSelected = selectable && visibleIds.length > 0 && visibleIds.every((id) => selection?.isSelected(id));
  const someSelected = selectable && !allSelected && visibleIds.some((id) => selection?.isSelected(id));

  const headCheckboxRef = useRef<HTMLInputElement | null>(null);
  useEffect(() => {
    if (headCheckboxRef.current) {
      headCheckboxRef.current.indeterminate = Boolean(someSelected);
    }
  }, [someSelected]);

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-card">
      <table className="w-full min-w-[720px] border-collapse text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50 text-left">
            {selectable && (
              <th className="w-10 px-3 py-2.5">
                <input
                  ref={headCheckboxRef}
                  type="checkbox"
                  aria-label="현재 페이지 전체 선택"
                  className="size-4 cursor-pointer accent-blue-600"
                  checked={Boolean(allSelected)}
                  onChange={() => selection?.toggleAllVisible(visibleIds)}
                />
              </th>
            )}
            {columns.map((column) => {
              const active = column.sortable && sortBy === column.key;
              return (
                <th
                  key={column.key}
                  className={`px-3 py-2.5 text-xs font-bold text-slate-600 ${column.sortable ? "cursor-pointer select-none hover:text-slate-900" : ""} ${column.headerClassName ?? ""}`}
                  onClick={column.sortable && onToggleSort ? () => onToggleSort(column.key) : undefined}
                  aria-sort={active ? (sortDir === "ASC" ? "ascending" : "descending") : undefined}
                >
                  <span className="inline-flex items-center gap-1">
                    {column.label}
                    {active && (
                      <span className={sortDir === "ASC" ? "text-red-500" : "text-blue-500"} aria-hidden>
                        {sortDir === "ASC" ? "▲" : "▼"}
                      </span>
                    )}
                  </span>
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody className={loading ? "opacity-60" : undefined}>
          {rows.length === 0 && (
            <tr>
              <td
                colSpan={columns.length + (selectable ? 1 : 0)}
                className="px-3 py-10 text-center text-sm text-slate-500"
              >
                {loading ? (
                  <span className="inline-flex items-center gap-2">
                    <RefreshCw className="size-4 animate-spin" />
                    불러오는 중입니다…
                  </span>
                ) : (
                  emptyText
                )}
              </td>
            </tr>
          )}
          {rows.map((row) => {
            const id = rowKey(row);
            const selected = selectable && selection?.isSelected(id);
            return (
              <tr
                key={id}
                className={`border-b border-slate-100 transition-colors last:border-0 ${selected ? "bg-blue-50/60" : "hover:bg-slate-50"} ${onRowClick ? "cursor-pointer" : ""}`}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
              >
                {selectable && (
                  <td className="w-10 px-3 py-2.5" onClick={(event) => event.stopPropagation()}>
                    <input
                      type="checkbox"
                      aria-label={`행 ${id} 선택`}
                      className="size-4 cursor-pointer accent-blue-600"
                      checked={Boolean(selected)}
                      onChange={() => selection?.toggle(id)}
                    />
                  </td>
                )}
                {columns.map((column) => (
                  <td key={column.key} className={`px-3 py-2.5 align-middle text-slate-700 ${column.cellClassName ?? ""}`}>
                    {column.render
                      ? column.render(row)
                      : String((row as Record<string, unknown>)[column.key] ?? "-")}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
