import { useEffect, useState } from "react";

import { getFitAnalysisByApplicationCase } from "../api/fitAnalysisApi";
import type { FitAnalysisDetail } from "../types/fitAnalysis";

/**
 * 지원 건 상세에서 해당 건의 최신 적합도 분석을 불러온다.
 * 적합도/전략/학습 추천 패널이 배열을 받으므로 단건 결과를 배열로 감싸 전달한다.
 * 아직 분석이 없으면(미실행) 빈 목록으로 처리해 패널이 안내 상태를 노출하도록 한다.
 */
export function useApplicationFitAnalysis(applicationCaseId: number | null, enabled: boolean) {
  const [analyses, setAnalyses] = useState<FitAnalysisDetail[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled || !applicationCaseId) {
      setAnalyses([]);
      return;
    }

    let ignore = false;
    setLoading(true);
    setError(null);

    getFitAnalysisByApplicationCase(applicationCaseId)
      .then((detail) => {
        if (!ignore) setAnalyses(detail ? [detail] : []);
      })
      .catch(() => {
        // 아직 적합도 분석이 없는 경우가 일반적이므로 빈 목록으로 두고 패널 안내 상태를 보여준다.
        if (!ignore) setAnalyses([]);
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [applicationCaseId, enabled]);

  return { analyses, loading, error };
}
