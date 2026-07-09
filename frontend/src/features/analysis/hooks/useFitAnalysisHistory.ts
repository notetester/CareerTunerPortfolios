import { useEffect, useState } from "react";

import { getFitAnalysisHistory } from "../api/fitAnalysisApi";
import type { FitAnalysisHistoryEntry } from "../types/fitAnalysis";

/**
 * 지원 건의 적합도 재분석 히스토리를 불러온다.
 * refreshKey(보통 최신 분석 id)가 바뀌면 다시 조회해 재분석 직후 목록을 갱신한다.
 */
export function useFitAnalysisHistory(applicationCaseId: number | null, enabled: boolean, refreshKey?: number | null) {
  const [entries, setEntries] = useState<FitAnalysisHistoryEntry[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!enabled || !applicationCaseId) {
      setEntries([]);
      return;
    }

    let ignore = false;
    setLoading(true);

    getFitAnalysisHistory(applicationCaseId)
      .then((history) => {
        if (!ignore) setEntries(history ?? []);
      })
      .catch(() => {
        // 히스토리는 부가 정보라 실패해도 패널 안내 상태만 보여준다.
        if (!ignore) setEntries([]);
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [applicationCaseId, enabled, refreshKey]);

  return { entries, loading };
}
