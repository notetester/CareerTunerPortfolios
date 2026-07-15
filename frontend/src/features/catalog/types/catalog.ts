// NCS 직무능력표준 · 자격증 카탈로그 검색/조회 타입 — 백엔드 CatalogDtos 와 1:1 대응.

/** NCS 세분류 검색 결과 요약. */
export interface NcsSearchItem {
  id: number;
  ncsCode: string;
  majorName: string;
  middleName: string;
  minorName: string;
  subName: string;
  unitCount: number | null;
  elementCount: number | null;
  minLevel: number | null;
  maxLevel: number | null;
}

/** NCS 능력단위요소(수행준거/지식/기술/태도). detail_json 키와 1:1. */
export interface NcsElement {
  elementNo?: string;
  elementName?: string;
  level?: number | string;
  performanceCriteria?: string[];
  knowledge?: string[];
  skills?: string[];
  attitudes?: string[];
  [key: string]: unknown;
}

/** NCS 능력단위. detail_json 키와 1:1. */
export interface NcsUnit {
  unitNo?: string;
  unitName?: string;
  level?: number | string;
  elements?: NcsElement[];
  [key: string]: unknown;
}

/** NCS 세분류 상세(units 는 detail_json 파싱 결과라 형태가 유연함). */
export interface NcsDetail extends NcsSearchItem {
  units: NcsUnit[] | unknown;
}

export type CertType = "NATIONAL_TECH" | "NATIONAL_PROF" | "PRIVATE";

/** 자격증 검색 결과 요약. */
export interface CertSearchItem {
  id: number;
  certType: CertType;
  name: string;
  grade: string | null;
  authority: string | null;
  official: string | null;
  hasSchedule: number | null;
  descriptionSnippet: string | null;
}

/** 자격증 상세(DB row). */
export interface CertDetail {
  id: number;
  certType: CertType;
  name: string;
  grade: string | null;
  authority: string | null;
  issuerOrg: string | null;
  series: string | null;
  jmCd: string | null;
  regNo: string | null;
  official: string | null;
  status: string | null;
  description: string | null;
  ncsSubName: string | null;
  hasSchedule: number | null;
}

/** 국가 시험일정 회차. */
export interface ExamSchedule {
  certName: string;
  year: number | null;
  roundName: string | null;
  docRegStart: string | null;
  docRegEnd: string | null;
  docExam: string | null;
  docPass: string | null;
  pracExamStart: string | null;
  pracExamEnd: string | null;
  pracPass: string | null;
}

/** 자격증 상세 응답 = row + (국가)시험일정. */
export interface CertDetailResponse {
  certificate: CertDetail;
  schedules: ExamSchedule[];
}

export const CERT_TYPE_LABELS: Record<CertType, string> = {
  NATIONAL_TECH: "국가기술자격",
  NATIONAL_PROF: "국가전문자격",
  PRIVATE: "민간자격",
};
