import { api } from "@/app/lib/api";

/** 관리자 법적문서 API — `/api/admin/legal/**` (ADMIN 자동 인증).
 *  계약: 작업지시서 STEP 2 / API 명세. */

export type LegalDocType =
  | "terms"
  | "privacy"
  | "marketing"
  | "ai-data-consent"
  | "copyright";

/** 버전 라이프사이클 배지 — 백엔드가 effective_date vs NOW() 로 계산해 내려준다. */
export type VersionBadge = "live" | "next" | "old" | "draft";

/** 버전 목록 항목 — `GET /api/admin/legal/{docType}/versions`. */
export interface AdminLegalVersionSummary {
  id: number;
  docType: LegalDocType;
  versionLabel: string;
  status: "DRAFT" | "PUBLISHED";
  /** live/next/old/draft — 게시중·예정·종료·작성중 배지. */
  badge: VersionBadge;
  summary: string | null;
  /** 회원에게 불리한 변경 (읽기·쓰기 모두 `isAdverse`). */
  isAdverse: boolean;
  effectiveDate: string | null;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 조항(편집 단위). */
export interface AdminLegalClause {
  seq: number;
  title: string;
  body: string;
}

/** 버전 상세 + 조항 — `GET /api/admin/legal/versions/{id}`. */
export interface AdminLegalVersionDetail extends AdminLegalVersionSummary {
  clauses: AdminLegalClause[];
}

/** 새 초안 생성 옵션. */
export interface CreateLegalDraftRequest {
  /** 현행 시행본 조항을 복제해 시작할지. */
  cloneFromCurrent?: boolean;
}

/** 초안 저장(DRAFT 만) — PUT. */
export interface SaveLegalDraftRequest {
  versionLabel: string;
  summary: string | null;
  isAdverse: boolean;
  effectiveDate: string | null;
  clauses: AdminLegalClause[];
}

/** 게시 — POST. effectiveDate 미지정/즉시면 null(서버가 now 처리). */
export interface PublishLegalRequest {
  effectiveDate: string | null;
}

/** 게시 결과 — `POST /api/admin/legal/versions/{id}/publish` 응답(언래핑된 data).
 *  백엔드 PublishLegalResponse{ version, warning } 와 1:1. 불리한 변경 리드타임(30일/일반 7일)
 *  미달 시 warning 에 안내 문자열이 담긴다(차단 아님 · 경고). 충분하면 null. */
export interface PublishLegalResponse {
  version: AdminLegalVersionDetail;
  warning: string | null;
}

/** 버전 목록(live/next/old 계산 포함). */
export function getVersions(docType: LegalDocType): Promise<AdminLegalVersionSummary[]> {
  return api<AdminLegalVersionSummary[]>(`/admin/legal/${docType}/versions`);
}

/** 버전 상세 + 조항. */
export function getVersionDetail(id: number): Promise<AdminLegalVersionDetail> {
  return api<AdminLegalVersionDetail>(`/admin/legal/versions/${id}`);
}

/** 새 초안 생성(옵션: 현행 조항 복제). 생성된 버전 상세 반환. */
export function createDraft(
  docType: LegalDocType,
  req: CreateLegalDraftRequest = {},
): Promise<AdminLegalVersionDetail> {
  return api<AdminLegalVersionDetail>(`/admin/legal/${docType}/versions`, {
    method: "POST",
    body: JSON.stringify({ cloneFromCurrent: req.cloneFromCurrent ?? false }),
  });
}

/** 초안 저장(DRAFT 만). 저장된 버전 상세 반환. */
export function saveDraft(
  id: number,
  req: SaveLegalDraftRequest,
): Promise<AdminLegalVersionDetail> {
  return api<AdminLegalVersionDetail>(`/admin/legal/versions/${id}`, {
    method: "PUT",
    body: JSON.stringify(req),
  });
}

/** 게시(DRAFT 만). effectiveDate=null 이면 즉시 시행.
 *  { version, warning } 반환 — warning 이 있으면 리드타임 부족 경고(차단 아님). */
export function publishVersion(
  id: number,
  req: PublishLegalRequest,
): Promise<PublishLegalResponse> {
  return api<PublishLegalResponse>(`/admin/legal/versions/${id}/publish`, {
    method: "POST",
    body: JSON.stringify(req),
  });
}

/** 삭제(DRAFT 만). */
export function deleteVersion(id: number): Promise<void> {
  return api<void>(`/admin/legal/versions/${id}`, { method: "DELETE" });
}
