import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import type {
  AdminColumn,
  AdminListMode,
  AdminListParams,
  AdminSortDir,
  PageResult,
} from "./types";

/**
 * 관리자 공통 목록 상태 훅.
 *
 * - 검색/정렬/페이지 상태를 URL searchParams 와 동기화(새로고침·URL 공유 유지)
 * - mode(SERVER/CLIENT)·pageSize 는 localStorage 영속
 * - SERVER 모드: 파라미터 변경 시 서버 재조회(서버가 page 클램프)
 * - CLIENT 모드: 검색 조건 변경 시에만 전량(상한 10000) 1회 로드,
 *   정렬/페이징은 로컬 useMemo 처리(TT의 DOM 캐시+filterKey 무효화 대체)
 */

export interface AdminAppliedSearch {
  keyword: string;
  searchType: string;
  filters: Record<string, string>;
  dateFrom: string;
  dateTo: string;
}

export interface AdminSelectionApi {
  ids: ReadonlySet<number>;
  count: number;
  isSelected: (id: number) => boolean;
  toggle: (id: number) => void;
  /** 현재 보이는 행 기준 전체선택/해제(indeterminate 는 그리드가 계산). */
  toggleAllVisible: (visibleIds: number[]) => void;
  clear: () => void;
}

export interface UseAdminListOptions<T> {
  /** localStorage 키 prefix. 예: "admin-grid:users" */
  storageKey: string;
  fetcher: (params: AdminListParams) => Promise<PageResult<T>>;
  rowKey: (row: T) => number;
  defaultSortBy: string;
  defaultSortDir?: AdminSortDir;
  defaultSize?: number;
  /** CLIENT 모드 전량 로드 상한(백엔드 상한과 동일하게 유지). */
  clientMaxSize?: number;
  /** URL/검색바에서 다루는 enum 필터 키 목록. */
  filterKeys?: string[];
  /** 첫 진입 기본 필터(URL 파라미터가 있으면 URL 이 우선). */
  initialFilters?: Record<string, string>;
  /** CLIENT 모드 로컬 정렬 접근자 참조용 컬럼 정의. */
  columns?: AdminColumn<T>[];
}

export interface UseAdminListResult<T> {
  rows: T[];
  total: number;
  page: number;
  totalPages: number;
  size: number;
  mode: AdminListMode;
  sortBy: string;
  sortDir: AdminSortDir;
  /** 기본 정렬에서 벗어나 있는지(정렬 초기화 버튼 노출 조건). */
  sorted: boolean;
  loading: boolean;
  error: string | null;
  applied: AdminAppliedSearch;
  applySearch: (next: Partial<AdminAppliedSearch>) => void;
  resetSearch: () => void;
  setPage: (page: number) => void;
  setSize: (size: number) => void;
  setMode: (mode: AdminListMode) => void;
  toggleSort: (key: string) => void;
  resetSort: () => void;
  refetch: () => void;
  selection: AdminSelectionApi;
  /** 내보내기용 현재 검색·정렬 파라미터 스냅샷(scope=page 면 page/size 포함). */
  exportParams: (scope: "all" | "search" | "selected" | "page") => AdminListParams;
}

const PAGE_SIZES = [10, 20, 50, 100];

function readStorage(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // 프라이빗 모드 등 저장 불가 환경은 조용히 무시
  }
}

function compareValues(a: unknown, b: unknown): number {
  const aNull = a === null || a === undefined || a === "";
  const bNull = b === null || b === undefined || b === "";
  if (aNull && bNull) return 0;
  if (aNull) return -1;
  if (bNull) return 1;
  if (typeof a === "number" && typeof b === "number") return a - b;
  if (typeof a === "boolean" && typeof b === "boolean") return Number(a) - Number(b);
  return String(a).localeCompare(String(b), "ko", { numeric: true, sensitivity: "base" });
}

export function useAdminList<T>(options: UseAdminListOptions<T>): UseAdminListResult<T> {
  const {
    storageKey,
    fetcher,
    rowKey,
    defaultSortBy,
    defaultSortDir = "DESC",
    defaultSize = 20,
    clientMaxSize = 10000,
    filterKeys = [],
    initialFilters = {},
    columns = [],
  } = options;

  const [searchParams, setSearchParams] = useSearchParams();

  // ── 초기 상태: URL > localStorage > 기본값 ──
  const [applied, setApplied] = useState<AdminAppliedSearch>(() => {
    const filters: Record<string, string> = { ...initialFilters };
    for (const key of filterKeys) {
      const value = searchParams.get(key);
      if (value !== null) filters[key] = value;
    }
    return {
      keyword: searchParams.get("keyword") ?? "",
      searchType: searchParams.get("searchType") ?? "all",
      filters,
      dateFrom: searchParams.get("dateFrom") ?? "",
      dateTo: searchParams.get("dateTo") ?? "",
    };
  });
  const [sortBy, setSortBy] = useState(() => searchParams.get("sortBy") ?? defaultSortBy);
  const [sortDir, setSortDir] = useState<AdminSortDir>(() =>
    searchParams.get("sortDir") === "ASC" ? "ASC" : searchParams.get("sortDir") === "DESC" ? "DESC" : defaultSortDir,
  );
  const [page, setPageState] = useState(() => {
    const raw = Number(searchParams.get("page"));
    return Number.isInteger(raw) && raw >= 1 ? raw : 1;
  });
  const [size, setSizeState] = useState(() => {
    const fromUrl = Number(searchParams.get("size"));
    if (PAGE_SIZES.includes(fromUrl)) return fromUrl;
    const stored = Number(readStorage(`${storageKey}:size`));
    return PAGE_SIZES.includes(stored) ? stored : defaultSize;
  });
  const [mode, setModeState] = useState<AdminListMode>(() => {
    const stored = readStorage(`${storageKey}:mode`);
    return stored === "CLIENT" ? "CLIENT" : "SERVER";
  });

  const [serverRows, setServerRows] = useState<T[]>([]);
  const [serverTotal, setServerTotal] = useState(0);
  const [serverTotalPages, setServerTotalPages] = useState(0);
  const [clientRows, setClientRows] = useState<T[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadTick, setReloadTick] = useState(0);
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<number>>(new Set());

  // 검색 조건 직렬화 키 — CLIENT 캐시 무효화 기준(TT filterKey 계약)
  const appliedKey = useMemo(
    () =>
      JSON.stringify([
        applied.keyword,
        applied.searchType,
        applied.filters,
        applied.dateFrom,
        applied.dateTo,
      ]),
    [applied],
  );

  // ── URL 동기화(상태 → searchParams, replace) ──
  useEffect(() => {
    const sp = new URLSearchParams();
    if (applied.keyword) sp.set("keyword", applied.keyword);
    if (applied.searchType && applied.searchType !== "all") sp.set("searchType", applied.searchType);
    for (const key of filterKeys) {
      const value = applied.filters[key];
      if (value) sp.set(key, value);
    }
    if (applied.dateFrom) sp.set("dateFrom", applied.dateFrom);
    if (applied.dateTo) sp.set("dateTo", applied.dateTo);
    if (sortBy !== defaultSortBy) sp.set("sortBy", sortBy);
    if (sortDir !== defaultSortDir) sp.set("sortDir", sortDir);
    if (page > 1) sp.set("page", String(page));
    if (size !== defaultSize) sp.set("size", String(size));
    setSearchParams(sp, { replace: true });
    // setSearchParams 는 안정 참조가 아니어서 의존성에 넣으면 무한 루프가 날 수 있다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [appliedKey, sortBy, sortDir, page, size]);

  // ── SERVER 모드 조회 ──
  useEffect(() => {
    if (mode !== "SERVER") return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    fetcher({
      keyword: applied.keyword || undefined,
      searchType: applied.searchType || undefined,
      filters: applied.filters,
      dateFrom: applied.dateFrom || undefined,
      dateTo: applied.dateTo || undefined,
      sortBy,
      sortDir,
      page,
      size,
      mode: "SERVER",
    })
      .then((result) => {
        if (cancelled) return;
        setServerRows(result.items);
        setServerTotal(result.total);
        setServerTotalPages(result.totalPages);
        // 서버가 존재하지 않는 페이지를 클램프했으면 로컬 페이지도 맞춘다.
        if (result.page !== page) setPageState(result.page);
      })
      .catch((requestError: unknown) => {
        if (!cancelled) {
          setError(requestError instanceof Error ? requestError.message : "목록을 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, appliedKey, sortBy, sortDir, page, size, reloadTick]);

  // ── CLIENT 모드 전량 로드(검색 조건 변경 시에만) ──
  useEffect(() => {
    if (mode !== "CLIENT") return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    fetcher({
      keyword: applied.keyword || undefined,
      searchType: applied.searchType || undefined,
      filters: applied.filters,
      dateFrom: applied.dateFrom || undefined,
      dateTo: applied.dateTo || undefined,
      sortBy: defaultSortBy,
      sortDir: defaultSortDir,
      page: 1,
      size: clientMaxSize,
      mode: "CLIENT",
    })
      .then((result) => {
        if (cancelled) return;
        setClientRows(result.items);
        setPageState(1);
      })
      .catch((requestError: unknown) => {
        if (!cancelled) {
          setError(requestError instanceof Error ? requestError.message : "목록을 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, appliedKey, reloadTick]);

  // CLIENT 모드 로컬 정렬 접근자
  const clientSortAccessor = useMemo(() => {
    const column = columns.find((item) => item.key === sortBy);
    if (column?.clientSortValue) return column.clientSortValue;
    return (row: T) => (row as Record<string, unknown>)[sortBy] as string | number | null | undefined;
  }, [columns, sortBy]);

  const clientSorted = useMemo(() => {
    if (mode !== "CLIENT" || !clientRows) return null;
    const factor = sortDir === "ASC" ? 1 : -1;
    return [...clientRows].sort((a, b) => factor * compareValues(clientSortAccessor(a), clientSortAccessor(b)));
  }, [mode, clientRows, sortDir, clientSortAccessor]);

  const total = mode === "CLIENT" ? clientSorted?.length ?? 0 : serverTotal;
  const totalPages = mode === "CLIENT" ? Math.max(1, Math.ceil(total / size)) : serverTotalPages;
  const rows = useMemo(() => {
    if (mode !== "CLIENT") return serverRows;
    if (!clientSorted) return [];
    const safePage = Math.min(Math.max(page, 1), Math.max(1, Math.ceil(clientSorted.length / size)));
    const start = (safePage - 1) * size;
    return clientSorted.slice(start, start + size);
  }, [mode, serverRows, clientSorted, page, size]);

  // ── 액션들 ──
  const applySearch = useCallback((next: Partial<AdminAppliedSearch>) => {
    setApplied((prev) => ({
      keyword: next.keyword ?? prev.keyword,
      searchType: next.searchType ?? prev.searchType,
      filters: next.filters ?? prev.filters,
      dateFrom: next.dateFrom ?? prev.dateFrom,
      dateTo: next.dateTo ?? prev.dateTo,
    }));
    setPageState(1);
    setSelectedIds(new Set());
  }, []);

  const resetSearch = useCallback(() => {
    setApplied({ keyword: "", searchType: "all", filters: { ...initialFilters }, dateFrom: "", dateTo: "" });
    setPageState(1);
    setSelectedIds(new Set());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const setPage = useCallback(
    (nextPage: number) => {
      setPageState(Math.min(Math.max(nextPage, 1), Math.max(totalPages, 1)));
    },
    [totalPages],
  );

  const setSize = useCallback(
    (nextSize: number) => {
      const normalized = PAGE_SIZES.includes(nextSize) ? nextSize : defaultSize;
      setSizeState(normalized);
      setPageState(1);
      writeStorage(`${storageKey}:size`, String(normalized));
    },
    [defaultSize, storageKey],
  );

  const setMode = useCallback(
    (nextMode: AdminListMode) => {
      setModeState(nextMode);
      setPageState(1);
      setClientRows(null);
      writeStorage(`${storageKey}:mode`, nextMode);
    },
    [storageKey],
  );

  const toggleSort = useCallback((key: string) => {
    setSortBy((prevKey) => {
      if (prevKey === key) {
        setSortDir((prevDir) => (prevDir === "ASC" ? "DESC" : "ASC"));
        return prevKey;
      }
      // 다른 컬럼 클릭 시 ASC 부터(TT 규약)
      setSortDir("ASC");
      return key;
    });
    setPageState(1);
  }, []);

  const resetSort = useCallback(() => {
    setSortBy(defaultSortBy);
    setSortDir(defaultSortDir);
    setPageState(1);
  }, [defaultSortBy, defaultSortDir]);

  const refetch = useCallback(() => {
    setClientRows(null);
    setReloadTick((tick) => tick + 1);
  }, []);

  const toggle = useCallback((id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const toggleAllVisible = useCallback((visibleIds: number[]) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      const allSelected = visibleIds.length > 0 && visibleIds.every((id) => next.has(id));
      if (allSelected) visibleIds.forEach((id) => next.delete(id));
      else visibleIds.forEach((id) => next.add(id));
      return next;
    });
  }, []);

  const clearSelection = useCallback(() => setSelectedIds(new Set()), []);

  const exportParams = useCallback(
    (scope: "all" | "search" | "selected" | "page"): AdminListParams => {
      const base: AdminListParams = {
        keyword: applied.keyword || undefined,
        searchType: applied.searchType || undefined,
        filters: applied.filters,
        dateFrom: applied.dateFrom || undefined,
        dateTo: applied.dateTo || undefined,
        sortBy,
        sortDir,
      };
      if (scope === "all") return { sortBy, sortDir };
      if (scope === "page") return { ...base, page, size };
      return base;
    },
    [applied, sortBy, sortDir, page, size],
  );

  const selection: AdminSelectionApi = useMemo(
    () => ({
      ids: selectedIds,
      count: selectedIds.size,
      isSelected: (id: number) => selectedIds.has(id),
      toggle,
      toggleAllVisible,
      clear: clearSelection,
    }),
    [selectedIds, toggle, toggleAllVisible, clearSelection],
  );

  // rowKey 는 selection 삭제 검증 등 외부에서 쓰이지 않지만 시그니처 안정화를 위해 참조 유지
  const rowKeyRef = useRef(rowKey);
  rowKeyRef.current = rowKey;

  return {
    rows,
    total,
    page,
    totalPages,
    size,
    mode,
    sortBy,
    sortDir,
    sorted: sortBy !== defaultSortBy || sortDir !== defaultSortDir,
    loading,
    error,
    applied,
    applySearch,
    resetSearch,
    setPage,
    setSize,
    setMode,
    toggleSort,
    resetSort,
    refetch,
    selection,
    exportParams,
  };
}
