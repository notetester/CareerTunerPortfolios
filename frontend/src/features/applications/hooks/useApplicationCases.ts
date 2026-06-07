import { useCallback, useEffect, useState } from "react";
import { listApplicationCases } from "../api/applicationCasesApi";
import type { ApplicationCase } from "../types/applicationCase";

export function useApplicationCases(enabled = true) {
  const [applicationCases, setApplicationCases] = useState<ApplicationCase[]>([]);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!enabled) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const items = await listApplicationCases();
      setApplicationCases(items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "지원 건 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [enabled]);

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
