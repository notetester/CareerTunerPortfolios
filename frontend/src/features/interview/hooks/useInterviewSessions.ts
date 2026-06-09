import { useCallback, useEffect, useState } from "react";
import { listInterviewSessions } from "../api/interviewApi";
import type { InterviewSession } from "../types/interview";

/** 내 면접 세션 목록을 불러온다. (applications 의 useApplicationCases 패턴 동일) */
export function useInterviewSessions(enabled = true) {
  const [sessions, setSessions] = useState<InterviewSession[]>([]);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!enabled) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const items = await listInterviewSessions();
      setSessions(items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "면접 기록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [enabled]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { sessions, setSessions, loading, error, refresh };
}
