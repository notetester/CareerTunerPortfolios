import { EVIDENCE_FIELD_LABELS, type EvidenceFieldLabelKey } from "./analysis";

// 근거(evidence) field → 한글 라벨 매핑 계약. 이 프로젝트엔 런타임 테스트 러너(vitest 등)가 없어
// 기존 *.contract.test.ts 와 동일하게 tsc --noEmit 으로만 검증되는 타입 계약이다.
// - 키 집합: EVIDENCE_FIELD_LABELS 의 as const satisfies 가 키 누락/오타를 정의부에서 잡는다.
// - 라벨 리터럴: 아래 배정문이 주요 분류기 라벨의 한글 값을 고정해 값 회귀를 잡는다.
// - 실제 변환 동작(trim().toUpperCase()·미매핑 폴백)은 런타임 러너 도입 시 별도 보강한다.

// 키 집합 계약: 매핑 키가 EvidenceFieldLabelKey 유니온을 정확히 채우는지(누락 시 tsc 에러).
const keyContract: Record<EvidenceFieldLabelKey, string> = EVIDENCE_FIELD_LABELS;

// 라벨 리터럴 계약: 대표 항목의 한글 값을 고정한다(값 변경/키 제거 시 tsc 에러).
const responsibility: "주요 업무" = EVIDENCE_FIELD_LABELS.RESPONSIBILITY;
const qualification: "자격 요건" = EVIDENCE_FIELD_LABELS.QUALIFICATION;
const preferred: "우대 사항" = EVIDENCE_FIELD_LABELS.PREFERRED;
const companyInfo: "회사 정보" = EVIDENCE_FIELD_LABELS.COMPANY_INFO;
const requiredSkills: "필수 역량" = EVIDENCE_FIELD_LABELS.REQUIREDSKILLS;

void keyContract;
void responsibility;
void qualification;
void preferred;
void companyInfo;
void requiredSkills;
