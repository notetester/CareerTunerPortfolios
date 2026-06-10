import { useCallback, useEffect, useState } from "react";
import { getJobPosting, getJobPostingRevisions, saveJobPosting, uploadJobPostingFile } from "../api/jobPostingsApi";
import type { ApplicationSourceType } from "../types/applicationCase";
import type { JobPosting, JobPostingRequest } from "../types/jobPosting";

export function useJobPosting(applicationCaseId: number | null, enabled = true) {
  const [jobPosting, setJobPosting] = useState<JobPosting | null>(null);
  const [revisions, setRevisions] = useState<JobPosting[]>([]);
  const [loading, setLoading] = useState(Boolean(applicationCaseId && enabled));
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
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
      setRevisions(await getJobPostingRevisions(applicationCaseId));
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
        setRevisions(await getJobPostingRevisions(applicationCaseId));
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

  const upload = useCallback(
    async (sourceType: Extract<ApplicationSourceType, "PDF" | "IMAGE">, file: File) => {
      if (!applicationCaseId) return null;

      setUploading(true);
      setError(null);
      try {
        const posting = await uploadJobPostingFile(applicationCaseId, sourceType, file);
        setJobPosting(posting);
        setRevisions(await getJobPostingRevisions(applicationCaseId));
        return posting;
      } catch (err) {
        setError(err instanceof Error ? err.message : "공고문 파일을 업로드하지 못했습니다.");
        return null;
      } finally {
        setUploading(false);
      }
    },
    [applicationCaseId],
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    jobPosting,
    revisions,
    loading,
    saving,
    uploading,
    error,
    refresh,
    save,
    upload,
  };
}
