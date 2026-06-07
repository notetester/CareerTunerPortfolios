import { useCallback, useEffect, useState } from "react";
import { getJobPosting, saveJobPosting } from "../api/jobPostingsApi";
import type { JobPosting, JobPostingRequest } from "../types/jobPosting";

export function useJobPosting(applicationCaseId: number | null, enabled = true) {
  const [jobPosting, setJobPosting] = useState<JobPosting | null>(null);
  const [loading, setLoading] = useState(Boolean(applicationCaseId && enabled));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!applicationCaseId || !enabled) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const posting = await getJobPosting(applicationCaseId);
      setJobPosting(posting);
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고문을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [applicationCaseId, enabled]);

  const save = useCallback(
    async (request: JobPostingRequest) => {
      if (!applicationCaseId) return null;

      setSaving(true);
      setError(null);
      try {
        const posting = await saveJobPosting(applicationCaseId, request);
        setJobPosting(posting);
        return posting;
      } catch (err) {
        setError(err instanceof Error ? err.message : "공고문을 저장하지 못했습니다.");
        return null;
      } finally {
        setSaving(false);
      }
    },
    [applicationCaseId],
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    jobPosting,
    loading,
    saving,
    error,
    refresh,
    save,
  };
}
