import { useCallback, useEffect, useState } from "react";
import { createMockCompanyAnalysis, getCompanyAnalysis } from "../api/analysisApi";
import type { CompanyAnalysis } from "../types/analysis";

export function useCompanyAnalysis(applicationCaseId: number | null, enabled = true) {
  const [companyAnalysis, setCompanyAnalysis] = useState<CompanyAnalysis | null>(null);
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
      const analysis = await getCompanyAnalysis(applicationCaseId);
      setCompanyAnalysis(analysis);
    } catch (err) {
      setError(err instanceof Error ? err.message : "기업 분석을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [applicationCaseId, enabled]);

  const createMock = useCallback(async () => {
    if (!applicationCaseId) return null;

    setGenerating(true);
    setError(null);
    try {
      const analysis = await createMockCompanyAnalysis(applicationCaseId);
      setCompanyAnalysis(analysis);
      return analysis;
    } catch (err) {
      setError(err instanceof Error ? err.message : "기업 분석을 생성하지 못했습니다.");
      return null;
    } finally {
      setGenerating(false);
    }
  }, [applicationCaseId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    companyAnalysis,
    loading,
    generating,
    error,
    refresh,
    createMock,
  };
}
