import type { ApplicationSourceType } from "../types/applicationCase";

type FileSourceType = Extract<ApplicationSourceType, "PDF" | "IMAGE">;

export const JOB_POSTING_MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
export const JOB_POSTING_MAX_FILE_SIZE_LABEL = "5MB";
export const JOB_POSTING_IMAGE_ACCEPT = "image/png,image/jpeg,image/webp,image/gif";

const ALLOWED_IMAGE_MIME_TYPES = new Set(["image/png", "image/jpeg", "image/webp", "image/gif"]);

export function validateJobPostingFile(sourceType: FileSourceType, file: File): string | null {
  if (file.size > JOB_POSTING_MAX_FILE_SIZE_BYTES) {
    return `공고 파일은 ${JOB_POSTING_MAX_FILE_SIZE_LABEL} 이하만 업로드할 수 있습니다.`;
  }

  if (sourceType === "PDF" && file.type !== "application/pdf") {
    return "PDF 방식에는 application/pdf 파일만 업로드할 수 있습니다.";
  }

  if (sourceType === "IMAGE" && !ALLOWED_IMAGE_MIME_TYPES.has(file.type)) {
    return "이미지 방식에는 PNG, JPG, WEBP, GIF 파일만 업로드할 수 있습니다.";
  }

  return null;
}
