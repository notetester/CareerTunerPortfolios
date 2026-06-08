import { useCallback, useEffect, useState } from "react";
import { createCompanyAnalysis, getCompanyAnalysis, getCompanyAnalysisHistory, reviewCompanyAnalysis } from "../api/analysisApi";
import type { CompanyAnalysis, CompanyAnalysisReviewRequest } from "../types/analysis";

export function useCompanyAnalysis(applicationCaseId: number | null, enabled = true) {
  const [companyAnalysis, setCompanyAnalysis] = useState<CompanyAnalysis | null>(null);
  const [history, setHistory] = useState<CompanyAnalysis[]>([]);
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
      setHistory(await getCompanyAnalysisHistory(applicationCaseId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "기업 분석을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [applicationCaseId, enabled]);

  const generate = useCallback(async () => {
    if (!applicationCaseId) return null;

    setGenerating(true);
    setError(null);
    try {
      const analysis = await createCompanyAnalysis(applicationCaseId);
      setCompanyAnalysis(analysis);
      setHistory(await getCompanyAnalysisHistory(applicationCaseId));
      return analysis;
    } catch (err) {
      setError(err instanceof Error ? err.message : "기업 분석을 생성하지 못했습니다.");
      return null;
    } finally {
      setGenerating(false);
    }
  }, [applicationCaseId]);

  const review = useCallback(
    async (analysisId: number, request: CompanyAnalysisReviewRequest) => {
      if (!applicationCaseId) return null;

      setGenerating(true);
      setError(null);
      try {
        const analysis = await reviewCompanyAnalysis(applicationCaseId, analysisId, request);
        setCompanyAnalysis(analysis);
        setHistory(await getCompanyAnalysisHistory(applicationCaseId));
        return analysis;
      } catch (err) {
        setError(err instanceof Error ? err.message : "기업 분석을 저장하지 못했습니다.");
        return null;
      } finally {
        setGenerating(false);
      }
    },
    [applicationCaseId],
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    companyAnalysis,
    history,
    loading,
    generating,
    error,
    refresh,
    generate,
    review,
  };
}
