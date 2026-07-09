import { useCallback, useEffect, useState } from "react";
import { getBAnalysisFailureLogs } from "../api/analysisApi";
import type { BAnalysisFailureLog } from "../types/analysis";

export function useBAnalysisFailureLogs(applicationCaseId: number | null, enabled = true, limit = 5) {
  const [failureLogs, setFailureLogs] = useState<BAnalysisFailureLog[]>([]);
  const [loading, setLoading] = useState(Boolean(applicationCaseId && enabled));
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!applicationCaseId || !enabled) {
      setFailureLogs([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      setFailureLogs(await getBAnalysisFailureLogs(applicationCaseId, limit));
    } catch (err) {
      setError(err instanceof Error ? err.message : "분석 실패 이력을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [applicationCaseId, enabled, limit]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    failureLogs,
    loading,
    error,
    refresh,
  };
}
