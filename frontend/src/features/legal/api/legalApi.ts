import { api } from "@/app/lib/api";

/** 공개 API가 지원하는 법적문서 타입 (백엔드 LegalDocType 와 정렬). */
export type LegalDocType = "terms" | "privacy" | "marketing";

export const LEGAL_DOC_TYPES = ["terms", "privacy", "marketing"] as const;

/** 라우트 path 세그먼트 → API docType.
 *  routes.ts 는 `legal/terms`·`legal/privacy`·`legal/ai-data-consent`·`legal/copyright` 를 노출하나
 *  백엔드 공개 계약은 terms|privacy|marketing 만 다룬다. ai-data-consent 는 marketing 으로 매핑하지
 *  않고(별개 동의), 미지원 path 는 그대로 두어 호출부에서 안내 처리한다. */
export const ROUTE_TO_DOC_TYPE: Record<string, LegalDocType> = {
  terms: "terms",
  privacy: "privacy",
  marketing: "marketing",
};

export function isLegalDocType(v: string): v is LegalDocType {
  return (LEGAL_DOC_TYPES as readonly string[]).includes(v);
}

/** 공개 조회 응답 — `GET /api/legal/{docType}` 계약과 일치. */
export interface LegalClauseDto {
  seq: number;
  title: string;
  body: string;
}

export interface LegalDocResponse {
  docType: LegalDocType;
  title: string;
  versionLabel: string | null;
  effectiveDate: string | null;
  updatedAt: string | null;
  summary: string | null;
  sections: LegalClauseDto[];
}

/** 현재 시행본 + 조항. 시행본이 없으면 sections=[] (404 아님). 공개 조회라 auth 불필요. */
export function getLegalDoc(docType: LegalDocType): Promise<LegalDocResponse> {
  return api<LegalDocResponse>(`/legal/${docType}`, {}, { auth: false });
}
