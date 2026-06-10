import { useCallback, useEffect, useState } from "react";
import { listApplicationCases } from "../api/applicationCasesApi";
import type { ApplicationCase, ApplicationCaseListView } from "../types/applicationCase";

type UseApplicationCasesOptions = boolean | {
  includeArchived?: boolean;
  view?: ApplicationCaseListView;
};

export function useApplicationCases(enabled = true, options: UseApplicationCasesOptions = false) {
  const [applicationCases, setApplicationCases] = useState<ApplicationCase[]>([]);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<string | null>(null);
  const includeArchived = typeof options === "boolean" ? options : Boolean(options.includeArchived);
  const view = typeof options === "boolean" ? undefined : options.view;

  const refresh = useCallback(async () => {
    if (!enabled) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const items = await listApplicationCases(view ? { view, includeArchived } : includeArchived);
      setApplicationCases(items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "지원 건 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [enabled, includeArchived, view]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    applicationCases,
    setApplicationCases,
    loading,
    error,
    refresh,
  };
}
