import { useCallback, useEffect, useState } from "react";
import { createMockJobAnalysis, getJobAnalysis } from "../api/analysisApi";
import type { JobAnalysis } from "../types/analysis";

export function useJobAnalysis(applicationCaseId: number | null, enabled = true) {
  const [jobAnalysis, setJobAnalysis] = useState<JobAnalysis | null>(null);
  const [loading, setLoading] = useState(Boolean(applicationCaseId && enabled));
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!applicationCaseId || !enabled) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const analysis = await getJobAnalysis(applicationCaseId);
      setJobAnalysis(analysis);
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고 분석을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [applicationCaseId, enabled]);

  const createMock = useCallback(async () => {
    if (!applicationCaseId) return null;

    setGenerating(true);
    setError(null);
    try {
      const analysis = await createMockJobAnalysis(applicationCaseId);
      setJobAnalysis(analysis);
      return analysis;
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고 분석을 생성하지 못했습니다.");
      return null;
    } finally {
      setGenerating(false);
    }
  }, [applicationCaseId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    jobAnalysis,
    loading,
    generating,
    error,
    refresh,
    createMock,
  };
}
