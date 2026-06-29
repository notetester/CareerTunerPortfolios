import { useCallback, useEffect, useState } from "react";
import {
  confirmApplicationCaseExtraction,
  getLatestApplicationCaseExtraction,
  reviewApplicationCaseExtraction,
  retryApplicationCaseExtraction,
} from "../api/applicationCasesApi";
import type { ApplicationCaseExtraction } from "../types/applicationCase";
import { isApplicationCaseExtractionActive } from "../types/applicationCase";
import { registerApplicationCaseExtraction } from "../utils/applicationExtractionTracker";

const POLL_INTERVAL_MS = 3000;

export function useApplicationCaseExtraction(applicationCaseId: number | null, enabled = true) {
  const [extraction, setExtraction] = useState<ApplicationCaseExtraction | null>(null);
  const [loading, setLoading] = useState(Boolean(applicationCaseId && enabled));
  const [retrying, setRetrying] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!applicationCaseId || !enabled) {
      setExtraction(null);
      setLoading(false);
      return null;
    }

    setLoading(true);
    setError(null);
    try {
      const latest = await getLatestApplicationCaseExtraction(applicationCaseId);
      setExtraction(latest);
      return latest;
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고문 추출 상태를 불러오지 못했습니다.");
      return null;
    } finally {
      setLoading(false);
    }
  }, [applicationCaseId, enabled]);

  const retry = useCallback(async () => {
    if (!applicationCaseId) return null;

    setRetrying(true);
    setError(null);
    try {
      const next = await retryApplicationCaseExtraction(applicationCaseId);
      setExtraction(next);
      registerApplicationCaseExtraction(next);
      return next;
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고문 추출을 다시 시작하지 못했습니다.");
      return null;
    } finally {
      setRetrying(false);
    }
  }, [applicationCaseId]);

  const review = useCallback(async (extractedText: string) => {
    if (!applicationCaseId) return null;

    setReviewing(true);
    setReviewError(null);
    try {
      const next = await reviewApplicationCaseExtraction(applicationCaseId, extractedText);
      setExtraction(next);
      return next;
    } catch (err) {
      setReviewError(err instanceof Error ? err.message : "공고문 검수를 저장하지 못했습니다.");
      return null;
    } finally {
      setReviewing(false);
    }
  }, [applicationCaseId]);

  const confirm = useCallback(async (extractedText: string) => {
    if (!applicationCaseId) return null;

    setConfirming(true);
    setConfirmError(null);
    try {
      const next = await confirmApplicationCaseExtraction(applicationCaseId, extractedText);
      setExtraction(next);
      return next;
    } catch (err) {
      setConfirmError(err instanceof Error ? err.message : "공고문 수정 확정을 저장하지 못했습니다.");
      return null;
    } finally {
      setConfirming(false);
    }
  }, [applicationCaseId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!enabled || !extraction || !isApplicationCaseExtractionActive(extraction.status)) return;

    const intervalId = window.setInterval(() => {
      void refresh();
    }, POLL_INTERVAL_MS);

    return () => window.clearInterval(intervalId);
  }, [enabled, extraction, refresh]);

  return {
    extraction,
    setExtraction,
    loading,
    retrying,
    reviewing,
    confirming,
    error,
    reviewError,
    confirmError,
    refresh,
    retry,
    review,
    confirm,
  };
}
