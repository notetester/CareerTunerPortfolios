import { useCallback, useEffect, useState } from "react";
import { getApplicationCase } from "../api/applicationCasesApi";
import type { ApplicationCase } from "../types/applicationCase";

export function useApplicationCase(id: number | null, enabled = true) {
  const [applicationCase, setApplicationCase] = useState<ApplicationCase | null>(null);
  const [loading, setLoading] = useState(Boolean(id && enabled));
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!id || !enabled) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const item = await getApplicationCase(id);
      setApplicationCase(item);
    } catch (err) {
      setError(err instanceof Error ? err.message : "지원 건을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [enabled, id]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    applicationCase,
    setApplicationCase,
    loading,
    error,
    refresh,
  };
}
