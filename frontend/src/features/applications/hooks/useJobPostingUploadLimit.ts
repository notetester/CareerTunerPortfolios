import { useEffect, useState } from "react";
import { getJobPostingUploadLimit } from "../api/applicationCasesApi";
import { formatUploadLimitLabel } from "../utils/jobPostingUpload";

export interface JobPostingUploadLimit {
  /** 서버에서 받은 현재 한도 바이트. 로딩 전·조회 실패면 null(그땐 크기 검사를 생략하고 서버 판정에 맡긴다). */
  maxBytes: number | null;
  /** 표시용 라벨(예: "10MB"). 한도가 확정된 경우에만. 로딩·실패 시 null → 컴포넌트가 상태별 문구를 고른다. */
  label: string | null;
  loading: boolean;
  error: string | null;
}

/**
 * 현재 공고 업로드 한도(관리자 설정 반영)를 서버에서 받아온다. 실제 강제는 서버가 하므로(JobPostingFileStorage),
 * 이 값은 사용자 사전 검증·표기용이다. 로딩·실패 상태를 그대로 노출해 컴포넌트가 가짜 라벨 없이 안내하도록 한다.
 */
export function useJobPostingUploadLimit(): JobPostingUploadLimit {
  const [maxBytes, setMaxBytes] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getJobPostingUploadLimit()
      .then((bytes) => {
        if (cancelled) return;
        if (Number.isFinite(bytes) && bytes > 0) {
          setMaxBytes(bytes);
        } else {
          setError("업로드 한도를 확인하지 못했습니다.");
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "업로드 한도를 확인하지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return {
    maxBytes,
    label: maxBytes != null ? formatUploadLimitLabel(maxBytes) : null,
    loading,
    error,
  };
}
