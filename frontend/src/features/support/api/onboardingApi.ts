import { ApiError, api } from "@/app/lib/api";

// 파일 업로드 계약(조사 확정): POST /api/file/upload, multipart(file + kind) → FileAssetResponse.
// autoprep 의 uploadAttachment(kind=ATTACHMENT 고정)과 동일 엔드포인트지만, 서류 종류별 kind 를
// 실어야 해서 support 로컬로 얇게 둔다(autoprep 파일 미수정).

/** FileAsset.kind (백엔드 enum): AUDIO/VIDEO/RESUME/PORTFOLIO/POSTING/ATTACHMENT. 서류 업로드에 쓰는 것만. */
export type UploadKind = "RESUME" | "PORTFOLIO" | "POSTING" | "ATTACHMENT";

/** 업로드 응답(백엔드 FileAssetResponse 중 프론트가 쓰는 필드). */
export interface UploadedFile {
  id: number;
  originalName: string;
  contentType?: string;
  sizeBytes?: number;
}

/** 파일 업로드 → fileId. 플랜 게이팅(무료 1개 등)은 실행 시 백엔드가 적용. */
export function uploadDocument(file: File, kind: UploadKind) {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("kind", kind);
  // api() 래퍼가 FormData 면 Content-Type 을 자동 생략(boundary 유지)한다.
  return api<UploadedFile>("/file/upload", { method: "POST", body: fd });
}

// ── 공고 → 지원 건(case) 생성 ──
// 공고는 attachment 로 오케에 실으면 분석에 안 들어간다(WRITE 만 attachment 소비).
// FIT/JOB 은 applicationCaseId 를 요구하므로, 공고는 아래 from-job-posting 경로로 "지원 건"을 만든다.
// ⚠️ 공고 본문 추출(OCR/파싱)은 비동기(extractionJob) — 갓 만든 케이스는 JOB/FIT 이 대기/스킵될 수 있다.
export interface CaseFromJobPostingResult {
  applicationCase: { id: number; companyName?: string; jobTitle?: string };
  extractionJob?: { id?: number; status?: string } | null;
}

/** 붙여넣기 텍스트 공고 → 지원 건. */
export function createCaseFromText(originalText: string) {
  return api<CaseFromJobPostingResult>("/application-cases/from-job-posting", {
    method: "POST",
    body: JSON.stringify({ originalText, sourceType: "TEXT", favorite: false }),
  });
}

/** 공고 URL → 지원 건. 본문 fetch·추출은 백엔드가 서버측에서 수행(JobPostingTextExtractor.extractUrl — SSRF 가드 포함). */
export function createCaseFromUrl(url: string) {
  return api<CaseFromJobPostingResult>("/application-cases/from-job-posting", {
    method: "POST",
    body: JSON.stringify({ uploadedFileUrl: url, sourceType: "URL", favorite: false }),
  });
}

/** 공고 파일(PDF/이미지) → 지원 건(비동기 OCR 추출 큐잉). */
export function createCaseFromFile(file: File, sourceType: "PDF" | "IMAGE") {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("sourceType", sourceType);
  fd.append("favorite", "false");
  return api<CaseFromJobPostingResult>("/application-cases/from-job-posting/upload", { method: "POST", body: fd });
}

// ── 재제출 시 실패한 추출 재큐잉 ──
// B 소유 엔드포인트 재사용(applications 기능의 applicationCasesApi.ts 와 동일 계약을 support 로컬로
// 얇게 둔다 — 위 파일 업로드와 같은 이유: 기능 폴더 간 결합 없이 B 코드 무수정).

/** 지원 건의 최신 공고 추출 상태(없으면 null). ensureCase 재제출 시 FAILED 여부 판정에 쓴다. */
export function getLatestExtractionStatus(applicationCaseId: number): Promise<{ status: string } | null> {
  return api<{ status: string } | null>(`/application-cases/${applicationCaseId}/job-posting/extraction`, {
    method: "GET",
  }).catch((err) => {
    if (err instanceof ApiError && (err.status === 404 || err.code === "NOT_FOUND")) {
      return null;
    }
    throw err;
  });
}

// ── 가이드 포폴 스텝: GitHub 링크 → README 원문 ──
// 실패도 HTTP 200 + ok:false 로 내려온다(백엔드 계약) — throw 대신 결과값으로 성공/실패를 구분한다.
export interface GithubReadmeResult {
  ok: boolean;
  text?: string;
  errorCode?: string;
}

export function fetchGithubReadme(repoUrl: string) {
  return api<GithubReadmeResult>("/support/portfolio/github-readme", {
    method: "POST",
    body: JSON.stringify({ repoUrl }),
  });
}
