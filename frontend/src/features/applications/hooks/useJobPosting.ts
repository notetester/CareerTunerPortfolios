import { useCallback, useEffect, useRef, useState } from "react";
import { getJobPosting, getJobPostingRevisions, saveJobPosting, uploadJobPostingFile } from "../api/jobPostingsApi";
import type { ApplicationSourceType } from "../types/applicationCase";
import type { JobPosting, JobPostingRequest } from "../types/jobPosting";

interface UseJobPostingOptions {
  loadRevisions?: boolean;
}

export function useJobPosting(
  applicationCaseId: number | null,
  enabled = true,
  options: UseJobPostingOptions = {},
) {
  const loadRevisions = options.loadRevisions ?? true;
  const [jobPosting, setJobPosting] = useState<JobPosting | null>(null);
  const [revisions, setRevisions] = useState<JobPosting[]>([]);
  const [loading, setLoading] = useState(Boolean(applicationCaseId && enabled));
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestSeqRef = useRef(0);

  const refresh = useCallback(async () => {
    const requestSeq = ++requestSeqRef.current;

    if (!applicationCaseId || !enabled) {
      setJobPosting(null);
      setRevisions([]);
      setLoading(false);
      setSaving(false);
      setUploading(false);
      setError(null);
      return null;
    }

    setLoading(true);
    setError(null);
    try {
      const [posting, postingRevisions] = await Promise.all([
        getJobPosting(applicationCaseId),
        loadRevisions ? getJobPostingRevisions(applicationCaseId) : Promise.resolve([] as JobPosting[]),
      ]);
      if (requestSeq !== requestSeqRef.current) return null;
      setJobPosting(posting);
      setRevisions(postingRevisions);
      return posting;
    } catch (err) {
      if (requestSeq !== requestSeqRef.current) return null;
      setError(err instanceof Error ? err.message : "공고문을 불러오지 못했습니다.");
      return null;
    } finally {
      if (requestSeq === requestSeqRef.current) {
        setLoading(false);
      }
    }
  }, [applicationCaseId, enabled, loadRevisions]);

  const save = useCallback(
    async (request: JobPostingRequest) => {
      const requestSeq = ++requestSeqRef.current;
      if (!applicationCaseId || !enabled) return null;

      setSaving(true);
      setError(null);
      try {
        const posting = await saveJobPosting(applicationCaseId, request);
        const postingRevisions = loadRevisions ? await getJobPostingRevisions(applicationCaseId) : [];
        if (requestSeq !== requestSeqRef.current) return null;
        setJobPosting(posting);
        setRevisions(postingRevisions);
        return posting;
      } catch (err) {
        if (requestSeq !== requestSeqRef.current) return null;
        setError(err instanceof Error ? err.message : "공고문을 저장하지 못했습니다.");
        return null;
      } finally {
        if (requestSeq === requestSeqRef.current) {
          setSaving(false);
        }
      }
    },
    [applicationCaseId, enabled, loadRevisions],
  );

  const upload = useCallback(
    async (sourceType: Extract<ApplicationSourceType, "PDF" | "IMAGE">, file: File) => {
      const requestSeq = ++requestSeqRef.current;
      if (!applicationCaseId || !enabled) return null;

      setUploading(true);
      setError(null);
      try {
        const posting = await uploadJobPostingFile(applicationCaseId, sourceType, file);
        const postingRevisions = loadRevisions ? await getJobPostingRevisions(applicationCaseId) : [];
        if (requestSeq !== requestSeqRef.current) return null;
        setJobPosting(posting);
        setRevisions(postingRevisions);
        return posting;
      } catch (err) {
        if (requestSeq !== requestSeqRef.current) return null;
        setError(err instanceof Error ? err.message : "공고문 파일을 업로드하지 못했습니다.");
        return null;
      } finally {
        if (requestSeq === requestSeqRef.current) {
          setUploading(false);
        }
      }
    },
    [applicationCaseId, enabled, loadRevisions],
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
