import { useCallback, useEffect, useState } from "react";
import { createJobAnalysis, getJobAnalysis, getJobAnalysisHistory, reviewJobAnalysis } from "../api/analysisApi";
import type { JobAnalysis, JobAnalysisReviewRequest } from "../types/analysis";

export function useJobAnalysis(applicationCaseId: number | null, enabled = true) {
  const [jobAnalysis, setJobAnalysis] = useState<JobAnalysis | null>(null);
  const [history, setHistory] = useState<JobAnalysis[]>([]);
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
      setHistory(await getJobAnalysisHistory(applicationCaseId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고 분석을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [applicationCaseId, enabled]);

  const generate = useCallback(async () => {
    if (!applicationCaseId) return null;

    setGenerating(true);
    setError(null);
    try {
      const analysis = await createJobAnalysis(applicationCaseId);
      setJobAnalysis(analysis);
      setHistory(await getJobAnalysisHistory(applicationCaseId));
      return analysis;
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고 분석을 생성하지 못했습니다.");
      return null;
    } finally {
      setGenerating(false);
    }
  }, [applicationCaseId]);

  const review = useCallback(
    async (analysisId: number, request: JobAnalysisReviewRequest) => {
      if (!applicationCaseId) return null;

      setGenerating(true);
      setError(null);
      try {
        const analysis = await reviewJobAnalysis(applicationCaseId, analysisId, request);
        setJobAnalysis(analysis);
        setHistory(await getJobAnalysisHistory(applicationCaseId));
        return analysis;
      } catch (err) {
        setError(err instanceof Error ? err.message : "공고 분석을 저장하지 못했습니다.");
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
    jobAnalysis,
    history,
    loading,
    generating,
    error,
    refresh,
    generate,
    review,
  };
}
