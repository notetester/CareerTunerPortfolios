import { useCallback, useEffect, useState } from "react";
import { listInterviewSessions } from "../api/interviewApi";
import type { InterviewSession } from "../types/interview";

/** 최근 면접 기록 페이지 크기. 백엔드 기본값(10)과 맞춘다. */
const PAGE_SIZE = 10;

/**
 * 내 면접 세션 목록을 더보기 방식으로 누적 조회한다.
 * (알림 목록과 동일한 서버 페이징 + "더보기" 패턴)
 */
export function useInterviewSessions(enabled = true) {
  const [sessions, setSessions] = useState<InterviewSession[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(enabled); // 첫 페이지 로드
  const [loadingMore, setLoadingMore] = useState(false); // 더보기 로드
  const [error, setError] = useState<string | null>(null);

  /** 첫 페이지부터 다시 불러와 목록을 초기화한다. (세션 생성/복원 후 갱신에도 사용) */
  const refresh = useCallback(async () => {
    if (!enabled) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await listInterviewSessions(0, PAGE_SIZE);
      setSessions(res.sessions);
      setPage(res.page);
      setHasNext(res.hasNext);
      setTotal(res.total);
    } catch (err) {
      setError(err instanceof Error ? err.message : "면접 기록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [enabled]);

  /** 다음 페이지를 이어 붙인다. (더보기 버튼) */
  const loadMore = useCallback(async () => {
    if (!enabled || loadingMore || !hasNext) return;
    setLoadingMore(true);
    setError(null);
    try {
      const res = await listInterviewSessions(page + 1, PAGE_SIZE);
      setSessions((prev) => [...prev, ...res.sessions]);
      setPage(res.page);
      setHasNext(res.hasNext);
      setTotal(res.total);
    } catch (err) {
      setError(err instanceof Error ? err.message : "면접 기록을 더 불러오지 못했습니다.");
    } finally {
      setLoadingMore(false);
    }
  }, [enabled, loadingMore, hasNext, page]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  /** 아직 더 받아올 수 있는 세션 수 (더보기 버튼 라벨용) */
  const remaining = Math.max(0, total - sessions.length);

  return {
    sessions,
    setSessions,
    loading,
    loadingMore,
    error,
    hasNext,
    total,
    remaining,
    refresh,
    loadMore,
  };
}
