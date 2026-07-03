/**
 * 지원 건 회사명/직무명 placeholder 표시 치환(F-02).
 *
 * B 추출 워커는 자동판독 실패 시 "기업명 확인 필요"/"직무명 확인 필요" 원문을 case 에 기록한다
 * (백엔드 CaseSlotValidator 와 동일 값 — 변경 시 함께 갱신). 이 원문이 칩/세션 제목/발화 라벨에
 * 그대로 노출되면 시스템 내부 문구가 사용자 데이터처럼 보이므로, 표시 계층에서 "미확인"으로 바꾼다.
 * 진행 차단은 백엔드 게이트(2-1) 소관 — 여기는 표시만 담당한다.
 */
const PLACEHOLDER_COMPANY = "기업명 확인 필요";
const PLACEHOLDER_JOB_TITLE = "직무명 확인 필요";

const isUnresolved = (v?: string | null): boolean => {
  const t = v?.trim();
  return !t || t === PLACEHOLDER_COMPANY || t === PLACEHOLDER_JOB_TITLE;
};

/** 회사명 표시값 — 미확인(placeholder·빈값)이면 "회사명 미확인". */
export const displayCompany = (v?: string | null): string =>
  isUnresolved(v) ? "회사명 미확인" : v!.trim();

/** 직무명 표시값 — 미확인(placeholder·빈값)이면 "직무명 미확인". */
export const displayJobTitle = (v?: string | null): string =>
  isUnresolved(v) ? "직무명 미확인" : v!.trim();

/** 조합 문자열(세션 제목 "{회사} {직무}" 등)에 남은 placeholder 원문만 표시용으로 치환. */
export const displayCaseText = (v?: string | null): string =>
  (v ?? "")
    .split(PLACEHOLDER_COMPANY).join("회사명 미확인")
    .split(PLACEHOLDER_JOB_TITLE).join("직무명 미확인");
