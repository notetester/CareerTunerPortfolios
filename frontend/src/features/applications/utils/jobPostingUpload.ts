import type { ApplicationSourceType } from "../types/applicationCase";

type FileSourceType = Extract<ApplicationSourceType, "PDF" | "IMAGE">;

/** 관리자 한도를 못 받았을 때(로딩 전·조회 실패)만 쓰는 폴백 기본값. 실제 한도는 서버가 강제한다. */
export const JOB_POSTING_MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
export const JOB_POSTING_MAX_FILE_SIZE_LABEL = "5MB";
export const JOB_POSTING_IMAGE_ACCEPT = "image/png,image/jpeg,image/webp,image/gif";

const ALLOWED_IMAGE_MIME_TYPES = new Set(["image/png", "image/jpeg", "image/webp", "image/gif"]);

/** 바이트를 "10MB"·"7.5MB" 형태 라벨로. 소수 MB 설정도 정확히 표기한다(후행 0 제거). */
export function formatUploadLimitLabel(bytes: number): string {
  const mb = Math.round((bytes / (1024 * 1024)) * 100) / 100;
  return `${mb}MB`;
}

/**
 * 공고 업로드 파일 검증. {@code maxBytes} 는 <b>필수 nullable</b> —
 * 숫자면 크기 검사, {@code null}(한도 미확정: 로딩 전·조회 실패)이면 크기 검사를 생략하고 서버 판정에 맡긴다.
 * MIME 검사는 상태와 무관하게 항상 수행한다. (기본값을 두지 않아 호출부가 인자를 빠뜨리면 컴파일이 잡는다.)
 */
export function validateJobPostingFile(
  sourceType: FileSourceType,
  file: File,
  maxBytes: number | null,
): string | null {
  if (maxBytes != null && file.size > maxBytes) {
    return `공고 파일은 ${formatUploadLimitLabel(maxBytes)} 이하만 업로드할 수 있습니다.`;
  }

  if (sourceType === "PDF" && file.type !== "application/pdf") {
    return "PDF 방식에는 application/pdf 파일만 업로드할 수 있습니다.";
  }

  if (sourceType === "IMAGE" && !ALLOWED_IMAGE_MIME_TYPES.has(file.type)) {
    return "이미지 방식에는 PNG, JPG, WEBP, GIF 파일만 업로드할 수 있습니다.";
  }

  return null;
}
