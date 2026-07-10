import { useCallback, useEffect, useMemo, useState } from "react";
import {
  listLatestApplicationCaseExtractions,
  retryApplicationCaseExtraction,
} from "../api/applicationCasesApi";
import type { ApplicationCaseExtraction } from "../types/applicationCase";
import { isApplicationCaseExtractionActive } from "../types/applicationCase";
import { registerApplicationCaseExtraction } from "../utils/applicationExtractionTracker";

const POLL_INTERVAL_MS = 5000;
const BULK_EXTRACTION_ID_LIMIT = 200;

type ExtractionMap = Record<number, ApplicationCaseExtraction | null>;

export function useApplicationCaseExtractions(applicationCaseIds: number[], enabled = true) {
  const idsKey = applicationCaseIds.join(",");
  const ids = useMemo(
    () => Array.from(new Set(idsKey.split(",").map(Number).filter((id) => Number.isFinite(id) && id > 0))),
    [idsKey],
  );
  const [extractions, setExtractions] = useState<ExtractionMap>({});
  const [loading, setLoading] = useState(enabled && ids.length > 0);
  const [retryingId, setRetryingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!enabled || ids.length === 0) {
      setExtractions({});
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const batches = Array.from({ length: Math.ceil(ids.length / BULK_EXTRACTION_ID_LIMIT) }, (_, index) =>
        ids.slice(index * BULK_EXTRACTION_ID_LIMIT, (index + 1) * BULK_EXTRACTION_ID_LIMIT),
      );
      const results = (await Promise.all(batches.map((batch) => listLatestApplicationCaseExtractions(batch)))).flat();
      const nextExtractions = Object.fromEntries(ids.map((id) => [id, null])) as ExtractionMap;
      results.forEach((item) => {
        nextExtractions[item.applicationCaseId] = item;
      });
      setExtractions(nextExtractions);
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고문 추출 상태를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [enabled, ids]);

  const retry = useCallback(async (applicationCaseId: number, ocrProvider: string) => {
    setRetryingId(applicationCaseId);
    setError(null);
    try {
      const next = await retryApplicationCaseExtraction(applicationCaseId, ocrProvider);
      setExtractions((current) => ({ ...current, [applicationCaseId]: next }));
      registerApplicationCaseExtraction(next);
      return next;
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고문 추출을 다시 시작하지 못했습니다.");
      return null;
    } finally {
      setRetryingId(null);
    }
  }, []);

  const hasActiveExtraction = useMemo(
    () => Object.values(extractions).some((item) => item && isApplicationCaseExtractionActive(item.status)),
    [extractions],
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!enabled || !hasActiveExtraction) return;

    const intervalId = window.setInterval(() => {
      void refresh();
    }, POLL_INTERVAL_MS);

    return () => window.clearInterval(intervalId);
  }, [enabled, hasActiveExtraction, refresh]);

  return {
    extractions,
    loading,
    retryingId,
    error,
    refresh,
    retry,
  };
}
