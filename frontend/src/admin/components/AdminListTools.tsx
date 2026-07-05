import { useEffect, useMemo, useState, type ReactNode } from "react";
import { ArrowDown, ArrowUp, ArrowUpDown, CheckSquare, Download, ListFilter, Search, X } from "lucide-react";
import "./admin-list-tools.css";

export type AdminListRowId = string | number;
export type AdminListSortDir = "asc" | "desc";
export type AdminListPageSize = 10 | 20 | 50 | 100 | "all";
export type AdminListLoadMode = "page" | "full";
export type AdminListExportFormat = "csv" | "excel";
export type AdminListExportScope = "page" | "filtered" | "selected" | "all";

export interface AdminListColumn<T> {
  id: string;
  label: string;
  getText: (row: T) => string | number | null | undefined;
  sortable?: boolean;
  exportable?: boolean;
}

interface AdminListOptions<T> {
  columns: AdminListColumn<T>[];
  getRowId: (row: T) => AdminListRowId;
  defaultPageSize?: AdminListPageSize;
  defaultSortId?: string;
  defaultSortDir?: AdminListSortDir;
}

export interface AdminListState<T> {
  columns: AdminListColumn<T>[];
  fieldId: string;
  setFieldId: (value: string) => void;
  keyword: string;
  setKeyword: (value: string) => void;
  loadMode: AdminListLoadMode;
  setLoadMode: (value: AdminListLoadMode) => void;
  pageSize: AdminListPageSize;
  setPageSize: (value: AdminListPageSize) => void;
  sortId: string | null;
  sortDir: AdminListSortDir;
  toggleSort: (columnId: string) => void;
  resetSort: () => void;
  page: number;
  setPage: (value: number | ((current: number) => number)) => void;
  totalPages: number;
  filteredRows: T[];
  visibleRows: T[];
  selectedIds: Set<string>;
  selectedRows: T[];
  selectedCount: number;
  allVisibleSelected: boolean;
  someVisibleSelected: boolean;
  isSelected: (row: T) => boolean;
  toggleRow: (row: T) => void;
  toggleVisibleRows: () => void;
  clearSelection: () => void;
  download: (scope: AdminListExportScope, format: AdminListExportFormat, fileName: string) => void;
  pageInfoText: string;
}

export function useAdminListTools<T>(items: T[], options: AdminListOptions<T>): AdminListState<T> {
  const [fieldId, setFieldId] = useState("all");
  const [keyword, setKeyword] = useState("");
  const [loadMode, setLoadModeState] = useState<AdminListLoadMode>("page");
  const [pageSize, setPageSizeState] = useState<AdminListPageSize>(options.defaultPageSize ?? 20);
  const [sortId, setSortId] = useState<string | null>(options.defaultSortId ?? null);
  const [sortDir, setSortDir] = useState<AdminListSortDir>(options.defaultSortDir ?? "asc");
  const [page, setPage] = useState(1);
  const [selectedIdsRaw, setSelectedIdsRaw] = useState<string[]>([]);

  const normalizedKeyword = normalizeText(keyword);
  const selectedIds = useMemo(() => new Set(selectedIdsRaw), [selectedIdsRaw]);

  const filteredRows = useMemo(() => {
    if (!normalizedKeyword) return items;
    const column = options.columns.find((candidate) => candidate.id === fieldId);
    return items.filter((row) => {
      const text = column
        ? column.getText(row)
        : options.columns.map((candidate) => candidate.getText(row)).join(" ");
      return normalizeText(text).includes(normalizedKeyword);
    });
  }, [fieldId, items, normalizedKeyword, options.columns]);

  const sortedRows = useMemo(() => {
    if (!sortId) return filteredRows;
    const column = options.columns.find((candidate) => candidate.id === sortId);
    if (!column) return filteredRows;
    return [...filteredRows].sort((left, right) => {
      const result = compareAdminListValues(column.getText(left), column.getText(right));
      return sortDir === "asc" ? result : -result;
    });
  }, [filteredRows, options.columns, sortDir, sortId]);

  const effectivePageSize = loadMode === "full" ? "all" : pageSize;
  const numericPageSize = effectivePageSize === "all" ? Math.max(sortedRows.length, 1) : effectivePageSize;
  const totalPages = Math.max(1, Math.ceil(sortedRows.length / numericPageSize));
  const startIndex = effectivePageSize === "all" ? 0 : (page - 1) * numericPageSize;
  const visibleRows = effectivePageSize === "all" ? sortedRows : sortedRows.slice(startIndex, startIndex + numericPageSize);
  const visibleIds = visibleRows.map((row) => String(options.getRowId(row)));
  const allVisibleSelected = visibleIds.length > 0 && visibleIds.every((id) => selectedIds.has(id));
  const someVisibleSelected = visibleIds.some((id) => selectedIds.has(id));
  const selectedRows = items.filter((row) => selectedIds.has(String(options.getRowId(row))));

  useEffect(() => {
    setPage((current) => Math.min(Math.max(current, 1), totalPages));
  }, [totalPages]);

  useEffect(() => {
    const validIds = new Set(items.map((row) => String(options.getRowId(row))));
    setSelectedIdsRaw((current) => {
      const next = current.filter((id) => validIds.has(id));
      return next.length === current.length ? current : next;
    });
  }, [items, options]);

  const resetPage = () => setPage(1);

  const setLoadMode = (value: AdminListLoadMode) => {
    setLoadModeState(value);
    resetPage();
  };

  const setPageSize = (value: AdminListPageSize) => {
    setPageSizeState(value);
    resetPage();
  };

  const toggleSort = (columnId: string) => {
    const column = options.columns.find((candidate) => candidate.id === columnId);
    if (!column?.sortable) return;
    if (sortId === columnId) {
      setSortDir((current) => (current === "asc" ? "desc" : "asc"));
    } else {
      setSortId(columnId);
      setSortDir("asc");
    }
    resetPage();
  };

  const resetSort = () => {
    setSortId(options.defaultSortId ?? null);
    setSortDir(options.defaultSortDir ?? "asc");
    resetPage();
  };

  const isSelected = (row: T) => selectedIds.has(String(options.getRowId(row)));

  const toggleRow = (row: T) => {
    const id = String(options.getRowId(row));
    setSelectedIdsRaw((current) => (
      current.includes(id) ? current.filter((candidate) => candidate !== id) : [...current, id]
    ));
  };

  const toggleVisibleRows = () => {
    setSelectedIdsRaw((current) => {
      const currentSet = new Set(current);
      if (visibleIds.length > 0 && visibleIds.every((id) => currentSet.has(id))) {
        return current.filter((id) => !visibleIds.includes(id));
      }
      visibleIds.forEach((id) => currentSet.add(id));
      return Array.from(currentSet);
    });
  };

  const clearSelection = () => setSelectedIdsRaw([]);

  const rowsForExport = (scope: AdminListExportScope) => {
    if (scope === "all") return items;
    if (scope === "filtered") return sortedRows;
    if (scope === "selected") return selectedRows;
    return visibleRows;
  };

  const download = (scope: AdminListExportScope, format: AdminListExportFormat, fileName: string) => {
    const rows = rowsForExport(scope);
    if (scope === "selected" && rows.length === 0) {
      window.alert("선택된 항목이 없습니다.");
      return;
    }
    downloadAdminListRows(options.columns, rows, format, fileName, scope);
  };

  const pageInfoText = `${sortedRows.length}건 중 ${visibleRows.length}건 표시 · ${page}/${totalPages}쪽`;

  return {
    columns: options.columns,
    fieldId,
    setFieldId: (value) => {
      setFieldId(value);
      resetPage();
    },
    keyword,
    setKeyword: (value) => {
      setKeyword(value);
      resetPage();
    },
    loadMode,
    setLoadMode,
    pageSize,
    setPageSize,
    sortId,
    sortDir,
    toggleSort,
    resetSort,
    page,
    setPage,
    totalPages,
    filteredRows: sortedRows,
    visibleRows,
    selectedIds,
    selectedRows,
    selectedCount: selectedRows.length,
    allVisibleSelected,
    someVisibleSelected,
    isSelected,
    toggleRow,
    toggleVisibleRows,
    clearSelection,
    download,
    pageInfoText,
  };
}

export function AdminListToolbar<T>({
  state,
  fileName,
  extraActions,
}: {
  state: AdminListState<T>;
  fileName: string;
  extraActions?: ReactNode;
}) {
  const [exportScope, setExportScope] = useState<AdminListExportScope>("page");
  const [exportFormat, setExportFormat] = useState<AdminListExportFormat>("csv");

  return (
    <div className="adm-list-tools">
      <div className="adm-list-tools__bulk" aria-live="polite" data-visible={state.selectedCount > 0}>
        <CheckSquare />
        <span>선택 {state.selectedCount}건</span>
        <button type="button" onClick={state.clearSelection}>
          <X /> 선택 해제
        </button>
        {extraActions}
      </div>

      <div className="adm-list-tools__row">
        <label className="adm-list-tools__control">
          <span>로드</span>
          <select value={state.loadMode} onChange={(event) => state.setLoadMode(event.target.value as AdminListLoadMode)}>
            <option value="page">페이지 로드</option>
            <option value="full">전체 로드</option>
          </select>
        </label>

        <div className="adm-list-tools__search">
          <ListFilter />
          <select value={state.fieldId} onChange={(event) => state.setFieldId(event.target.value)}>
            <option value="all">현재 화면 전체</option>
            {state.columns.map((column) => (
              <option key={column.id} value={column.id}>{column.label}</option>
            ))}
          </select>
          <span className="adm-list-tools__search-input">
            <Search />
            <input
              value={state.keyword}
              onChange={(event) => state.setKeyword(event.target.value)}
              placeholder="현재 화면 내 검색"
            />
          </span>
          {state.keyword && (
            <button type="button" className="adm-list-tools__icon-btn" aria-label="검색어 지우기" onClick={() => state.setKeyword("")}>
              <X />
            </button>
          )}
        </div>

        <label className="adm-list-tools__control">
          <span>표시</span>
          <select value={String(state.pageSize)} onChange={(event) => state.setPageSize(parsePageSize(event.target.value))}>
            <option value="10">10</option>
            <option value="20">20</option>
            <option value="50">50</option>
            <option value="100">100</option>
            <option value="all">전체</option>
          </select>
        </label>

        <div className="adm-list-tools__export">
          <select value={exportScope} onChange={(event) => setExportScope(event.target.value as AdminListExportScope)}>
            <option value="page">현재 페이지</option>
            <option value="filtered">검색 결과</option>
            <option value="selected">선택 항목</option>
            <option value="all">전체</option>
          </select>
          <select value={exportFormat} onChange={(event) => setExportFormat(event.target.value as AdminListExportFormat)}>
            <option value="csv">CSV</option>
            <option value="excel">Excel</option>
          </select>
          <button type="button" onClick={() => state.download(exportScope, exportFormat, fileName)}>
            <Download /> 내보내기
          </button>
        </div>
      </div>
    </div>
  );
}

export function AdminSortableHeader<T>({
  state,
  columnId,
  children,
  className,
}: {
  state: AdminListState<T>;
  columnId: string;
  children: ReactNode;
  className?: string;
}) {
  const column = state.columns.find((candidate) => candidate.id === columnId);
  const active = state.sortId === columnId;
  const Icon = active ? (state.sortDir === "asc" ? ArrowUp : ArrowDown) : ArrowUpDown;
  return (
    <th className={className} aria-sort={active ? (state.sortDir === "asc" ? "ascending" : "descending") : "none"}>
      <button
        type="button"
        className={`adm-list-sort ${active ? "is-active" : ""}`}
        disabled={!column?.sortable}
        onClick={() => state.toggleSort(columnId)}
      >
        <span>{children}</span>
        <Icon />
      </button>
    </th>
  );
}

export function AdminSelectionHeader<T>({ state }: { state: AdminListState<T> }) {
  return (
    <th className="adm-list-select-col">
      <input
        type="checkbox"
        aria-label="현재 페이지 선택"
        checked={state.allVisibleSelected}
        ref={(input) => {
          if (input) input.indeterminate = !state.allVisibleSelected && state.someVisibleSelected;
        }}
        onChange={state.toggleVisibleRows}
      />
    </th>
  );
}

export function AdminSelectionCell<T>({ state, row }: { state: AdminListState<T>; row: T }) {
  return (
    <td className="adm-list-select-col" onClick={(event) => event.stopPropagation()}>
      <input
        type="checkbox"
        aria-label="행 선택"
        checked={state.isSelected(row)}
        onChange={() => state.toggleRow(row)}
      />
    </td>
  );
}

export function AdminListFooter<T>({ state }: { state: AdminListState<T> }) {
  return (
    <div className="adm-list-footer">
      <span className="num">{state.pageInfoText}</span>
      <div className="adm-list-footer__pager">
        <button type="button" disabled={state.page <= 1} onClick={() => state.setPage((current) => current - 1)}>
          이전
        </button>
        <button type="button" disabled={state.page >= state.totalPages} onClick={() => state.setPage((current) => current + 1)}>
          다음
        </button>
      </div>
    </div>
  );
}

function parsePageSize(value: string): AdminListPageSize {
  if (value === "all") return "all";
  const parsed = Number(value);
  return parsed === 10 || parsed === 20 || parsed === 50 || parsed === 100 ? parsed : 20;
}

function normalizeText(value: unknown): string {
  return String(value ?? "").replace(/\s+/g, " ").trim().toLowerCase();
}

function compareAdminListValues(left: unknown, right: unknown): number {
  const a = parseComparableValue(left);
  const b = parseComparableValue(right);
  if (a.type === "number" && b.type === "number") return a.value - b.value;
  return String(a.value).localeCompare(String(b.value), "ko", { numeric: true, sensitivity: "base" });
}

function parseComparableValue(value: unknown): { type: "number"; value: number } | { type: "string"; value: string } {
  const raw = String(value ?? "").trim();
  const normalizedDate = raw
    .replace(/\./g, "-")
    .replace(/\//g, "-")
    .replace(/\s+/, "T");
  const time = Date.parse(normalizedDate);
  if (!Number.isNaN(time) && /\d{4}[-/.\s]\d{1,2}[-/.\s]\d{1,2}/.test(raw)) {
    return { type: "number", value: time };
  }
  const numeric = raw.replace(/,/g, "");
  if (/^-?\d+(\.\d+)?$/.test(numeric)) {
    return { type: "number", value: Number(numeric) };
  }
  return { type: "string", value: raw.toLowerCase() };
}

function downloadAdminListRows<T>(
  columns: AdminListColumn<T>[],
  rows: T[],
  format: AdminListExportFormat,
  fileName: string,
  scope: AdminListExportScope,
) {
  const exportColumns = columns.filter((column) => column.exportable !== false);
  const headers = exportColumns.map((column) => column.label);
  const body = rows.map((row) => exportColumns.map((column) => String(column.getText(row) ?? "")));
  const safeName = safeFileName(`${fileName}_${scope}_${new Date().toISOString().slice(0, 10)}`);
  if (format === "excel") {
    downloadBlob("\ufeff" + buildExcelXml(headers, body, safeName.slice(0, 31)), `${safeName}.xls`, "application/vnd.ms-excel;charset=utf-8");
    return;
  }
  const csv = [headers, ...body].map((row) => row.map(csvEscape).join(",")).join("\n");
  downloadBlob("\ufeff" + csv, `${safeName}.csv`, "text/csv;charset=utf-8");
}

function csvEscape(value: string): string {
  return `"${value.replace(/"/g, '""')}"`;
}

function safeFileName(value: string): string {
  return value.replace(/[\\/:*?"<>|]+/g, "_").replace(/\s+/g, "_").replace(/_+/g, "_").replace(/^_+|_+$/g, "") || "admin_list";
}

function downloadBlob(content: string, filename: string, type: string) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function buildExcelXml(headers: string[], rows: string[][], worksheetName: string): string {
  const sheet = xmlEscape(worksheetName || "export").slice(0, 31) || "export";
  const headerXml = `<Row>${headers.map((value) => excelCell(value, "header")).join("")}</Row>`;
  const bodyXml = rows.map((row) => `<Row>${row.map((value) => excelCell(value)).join("")}</Row>`).join("");
  return `<?xml version="1.0" encoding="UTF-8"?><?mso-application progid="Excel.Sheet"?><Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"><Styles><Style ss:ID="Default" ss:Name="Normal"><Alignment ss:Vertical="Center"/><Font ss:FontName="Malgun Gothic" ss:Size="10"/></Style><Style ss:ID="header"><Font ss:FontName="Malgun Gothic" ss:Size="10" ss:Bold="1"/><Interior ss:Color="#D9EAF7" ss:Pattern="Solid"/></Style></Styles><Worksheet ss:Name="${sheet}"><Table>${headerXml}${bodyXml}</Table><WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel"><FreezePanes/><FrozenNoSplit/><SplitHorizontal>1</SplitHorizontal><TopRowBottomPane>1</TopRowBottomPane></WorksheetOptions></Worksheet></Workbook>`;
}

function excelCell(value: string, styleId?: string): string {
  const style = styleId ? ` ss:StyleID="${styleId}"` : "";
  return `<Cell${style}><Data ss:Type="String">${xmlEscape(value)}</Data></Cell>`;
}

function xmlEscape(value: string): string {
  return value
    .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}
