import { useCallback, useEffect, useRef, useState, type SetStateAction } from "react";
import { listApplicationCases } from "../api/applicationCasesApi";
import type { ApplicationCase, ApplicationCaseListView } from "../types/applicationCase";

type UseApplicationCasesOptions = boolean | {
  includeArchived?: boolean;
  view?: ApplicationCaseListView;
};

export function useApplicationCases(
  enabled = true,
  options: UseApplicationCasesOptions = false,
  accountId: number | null = null,
) {
  const [applicationCases, setApplicationCases] = useState<ApplicationCase[]>([]);
  const [loadedAccountId, setLoadedAccountId] = useState<number | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<string | null>(null);
  const requestGeneration = useRef(0);
  const includeArchived = typeof options === "boolean" ? options : Boolean(options.includeArchived);
  const view = typeof options === "boolean" ? undefined : options.view;

  const refresh = useCallback(async () => {
    const generation = ++requestGeneration.current;
    if (!enabled || accountId == null) {
      setApplicationCases([]);
      setLoadedAccountId(null);
      setError(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const items = await listApplicationCases(view ? { view, includeArchived } : includeArchived);
      if (generation !== requestGeneration.current) return;
      setApplicationCases(items);
      setLoadedAccountId(accountId);
    } catch (err) {
      if (generation !== requestGeneration.current) return;
      setError(err instanceof Error ? err.message : "지원 건 목록을 불러오지 못했습니다.");
    } finally {
      if (generation === requestGeneration.current) setLoading(false);
    }
  }, [accountId, enabled, includeArchived, view]);

  useEffect(() => {
    void refresh();
    return () => {
      requestGeneration.current += 1;
    };
  }, [refresh]);

  const setScopedApplicationCases = useCallback((next: SetStateAction<ApplicationCase[]>) => {
    if (accountId == null) return;
    setLoadedAccountId(accountId);
    setApplicationCases(next);
  }, [accountId]);

  const visibleApplicationCases = enabled && accountId != null && loadedAccountId === accountId
    ? applicationCases
    : [];

  return {
    applicationCases: visibleApplicationCases,
    setApplicationCases: setScopedApplicationCases,
    loading,
    error,
    refresh,
  };
}
