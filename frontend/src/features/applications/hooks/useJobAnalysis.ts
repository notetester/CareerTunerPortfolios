import { useCallback, useEffect, useRef, useState } from "react";
import { createJobAnalysis, getJobAnalysis, getJobAnalysisHistory, reviewJobAnalysis } from "../api/analysisApi";
import type { JobAnalysis, JobAnalysisReviewRequest } from "../types/analysis";

export function useJobAnalysis(applicationCaseId: number | null, enabled = true) {
  const [jobAnalysis, setJobAnalysis] = useState<JobAnalysis | null>(null);
  const [history, setHistory] = useState<JobAnalysis[]>([]);
  const [loading, setLoading] = useState(Boolean(applicationCaseId && enabled));
  const [generating, setGenerating] = useState(false);
  const [reviewSaving, setReviewSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const requestSeqRef = useRef(0);

  const refresh = useCallback(async () => {
    const requestSeq = ++requestSeqRef.current;

    if (!applicationCaseId || !enabled) {
      setJobAnalysis(null);
      setHistory([]);
      setLoading(false);
      setGenerating(false);
      setReviewSaving(false);
      setError(null);
      setReviewError(null);
      return null;
    }

    setLoading(true);
    setError(null);
    try {
      const [analysis, analysisHistory] = await Promise.all([
        getJobAnalysis(applicationCaseId),
        getJobAnalysisHistory(applicationCaseId),
      ]);
      if (requestSeq !== requestSeqRef.current) return null;
      setJobAnalysis(analysis);
      setHistory(analysisHistory);
      return analysis;
    } catch (err) {
      if (requestSeq !== requestSeqRef.current) return null;
      setError(err instanceof Error ? err.message : "공고 분석을 불러오지 못했습니다.");
      return null;
    } finally {
      if (requestSeq === requestSeqRef.current) {
        setLoading(false);
      }
    }
  }, [applicationCaseId, enabled]);

  const generate = useCallback(async (provider: string) => {
    const requestSeq = ++requestSeqRef.current;
    if (!applicationCaseId || !enabled) return null;

    setGenerating(true);
    setError(null);
    setReviewError(null);
    try {
      const analysis = await createJobAnalysis(applicationCaseId, provider);
      const analysisHistory = await getJobAnalysisHistory(applicationCaseId);
      if (requestSeq !== requestSeqRef.current) return null;
      setJobAnalysis(analysis);
      setHistory(analysisHistory);
      return analysis;
    } catch (err) {
      if (requestSeq !== requestSeqRef.current) return null;
      setError(err instanceof Error ? err.message : "공고 분석을 생성하지 못했습니다.");
      return null;
    } finally {
      if (requestSeq === requestSeqRef.current) {
        setGenerating(false);
      }
    }
  }, [applicationCaseId, enabled]);

  const review = useCallback(
    async (analysisId: number, request: JobAnalysisReviewRequest) => {
      const requestSeq = ++requestSeqRef.current;
      if (!applicationCaseId || !enabled) return null;

      setReviewSaving(true);
      setReviewError(null);
      try {
        const analysis = await reviewJobAnalysis(applicationCaseId, analysisId, request);
        const analysisHistory = await getJobAnalysisHistory(applicationCaseId);
        if (requestSeq !== requestSeqRef.current) return null;
        setJobAnalysis(analysis);
        setHistory(analysisHistory);
        return analysis;
      } catch (err) {
        if (requestSeq !== requestSeqRef.current) return null;
        setReviewError(err instanceof Error ? err.message : "공고 분석을 저장하지 못했습니다.");
        return null;
      } finally {
        if (requestSeq === requestSeqRef.current) {
          setReviewSaving(false);
        }
      }
    },
    [applicationCaseId, enabled],
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    jobAnalysis,
    history,
    loading,
    generating,
    reviewSaving,
    error,
    reviewError,
    refresh,
    generate,
    review,
  };
}
