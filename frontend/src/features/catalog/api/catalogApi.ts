import { api } from "@/app/lib/api";
import type {
  CertDetailResponse,
  CertSearchItem,
  CertType,
  NcsDetail,
  NcsSearchItem,
} from "../types/catalog";

function buildQuery(params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value) !== "") {
      query.set(key, String(value));
    }
  });
  const queryString = query.toString();
  return queryString ? `?${queryString}` : "";
}

/** NCS 세분류 검색(세분류명·능력단위·기술 키워드). */
export function searchNcs(q: string, limit = 30): Promise<NcsSearchItem[]> {
  return api<NcsSearchItem[]>(`/catalog/ncs${buildQuery({ q, limit })}`, { method: "GET" });
}

/** NCS 세분류 상세(능력단위→요소→수행준거/지식/기술/태도). */
export function getNcsDetail(id: number): Promise<NcsDetail> {
  return api<NcsDetail>(`/catalog/ncs/${id}`, { method: "GET" });
}

/** 자격증 검색(이름·설명 키워드, 유형 필터). */
export function searchCertificates(q: string, type?: CertType | "", limit = 30): Promise<CertSearchItem[]> {
  return api<CertSearchItem[]>(`/catalog/certificates${buildQuery({ q, type, limit })}`, { method: "GET" });
}

/** 자격증 상세(설명 + 국가 시험일정). */
export function getCertificateDetail(id: number): Promise<CertDetailResponse> {
  return api<CertDetailResponse>(`/catalog/certificates/${id}`, { method: "GET" });
}
