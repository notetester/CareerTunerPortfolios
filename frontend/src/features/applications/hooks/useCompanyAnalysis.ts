import { useCallback, useEffect, useRef, useState } from "react";
import { createCompanyAnalysis, getCompanyAnalysis, getCompanyAnalysisHistory, reviewCompanyAnalysis } from "../api/analysisApi";
import type { CompanyAnalysis, CompanyAnalysisReviewRequest } from "../types/analysis";

export function useCompanyAnalysis(applicationCaseId: number | null, enabled = true) {
  const [companyAnalysis, setCompanyAnalysis] = useState<CompanyAnalysis | null>(null);
  const [history, setHistory] = useState<CompanyAnalysis[]>([]);
  const [loading, setLoading] = useState(Boolean(applicationCaseId && enabled));
  const [generating, setGenerating] = useState(false);
  const [reviewSaving, setReviewSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const requestSeqRef = useRef(0);

  const refresh = useCallback(async () => {
    const requestSeq = ++requestSeqRef.current;

    if (!applicationCaseId || !enabled) {
      setCompanyAnalysis(null);
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
        getCompanyAnalysis(applicationCaseId),
        getCompanyAnalysisHistory(applicationCaseId),
      ]);
      if (requestSeq !== requestSeqRef.current) return null;
      setCompanyAnalysis(analysis);
      setHistory(analysisHistory);
      return analysis;
    } catch (err) {
      if (requestSeq !== requestSeqRef.current) return null;
      setError(err instanceof Error ? err.message : "기업 분석을 불러오지 못했습니다.");
      return null;
    } finally {
      if (requestSeq === requestSeqRef.current) {
        setLoading(false);
      }
    }
  }, [applicationCaseId, enabled]);

  const generate = useCallback(async () => {
    const requestSeq = ++requestSeqRef.current;
    if (!applicationCaseId || !enabled) return null;

    setGenerating(true);
    setError(null);
    setReviewError(null);
    try {
      const analysis = await createCompanyAnalysis(applicationCaseId);
      const analysisHistory = await getCompanyAnalysisHistory(applicationCaseId);
      if (requestSeq !== requestSeqRef.current) return null;
      setCompanyAnalysis(analysis);
      setHistory(analysisHistory);
      return analysis;
    } catch (err) {
      if (requestSeq !== requestSeqRef.current) return null;
      setError(err instanceof Error ? err.message : "기업 분석을 생성하지 못했습니다.");
      return null;
    } finally {
      if (requestSeq === requestSeqRef.current) {
        setGenerating(false);
      }
    }
  }, [applicationCaseId, enabled]);

  const review = useCallback(
    async (analysisId: number, request: CompanyAnalysisReviewRequest) => {
      const requestSeq = ++requestSeqRef.current;
      if (!applicationCaseId || !enabled) return null;

      setReviewSaving(true);
      setReviewError(null);
      try {
        const analysis = await reviewCompanyAnalysis(applicationCaseId, analysisId, request);
        const analysisHistory = await getCompanyAnalysisHistory(applicationCaseId);
        if (requestSeq !== requestSeqRef.current) return null;
        setCompanyAnalysis(analysis);
        setHistory(analysisHistory);
        return analysis;
      } catch (err) {
        if (requestSeq !== requestSeqRef.current) return null;
        setReviewError(err instanceof Error ? err.message : "기업 분석을 저장하지 못했습니다.");
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
    companyAnalysis,
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
