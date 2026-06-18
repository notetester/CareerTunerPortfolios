/**
 * 동의 토글에 표시하는 약관 요약 메타(설정·소셜 가입 공용).
 * 여기 body 는 화면 표시용 요약이며, 구속력 있는 전문은 /legal 페이지가 기준이다.
 *
 * 주의: 이 파일은 dev 의 Settings.tsx·SocialConsent.tsx 가 참조하는데 누락되어 있어
 * (병렬 PR 에서 import 만 추가되고 파일이 빠짐) 빌드를 막고 있었다. 사용처 시그니처에 맞춰 복원했으며,
 * 약관 문구는 동의 흐름 담당이 정식 카피로 보완하면 된다.
 */
export interface ConsentTerm {
  code: "TERMS" | "PRIVACY" | "AI_DATA" | "MARKETING";
  title: string;
  version: string;
  required: boolean;
  effectiveDate: string;
  body: string[];
}

export const consentTerms: ConsentTerm[] = [
  {
    code: "TERMS",
    title: "서비스 이용약관",
    version: "v1.0",
    required: true,
    effectiveDate: "2026-01-01",
    body: [
      "CareerTuner 서비스 이용에 관한 기본 권리·의무를 정합니다.",
      "계정, 콘텐츠, 결제·크레딧 이용 조건을 포함합니다.",
      "전문은 이용약관(/legal/terms)에서 확인할 수 있습니다.",
    ],
  },
  {
    code: "PRIVACY",
    title: "개인정보 처리방침",
    version: "v1.0",
    required: true,
    effectiveDate: "2026-01-01",
    body: [
      "수집하는 개인정보 항목과 이용 목적, 보관 기간을 안내합니다.",
      "프로필·지원 건·면접 데이터의 처리 방식을 포함합니다.",
      "전문은 개인정보 처리방침(/legal/privacy)에서 확인할 수 있습니다.",
    ],
  },
  {
    code: "AI_DATA",
    title: "AI 데이터 활용 동의",
    version: "v1.0",
    required: false,
    effectiveDate: "2026-01-01",
    body: [
      "프로필 요약·기술 추출·완성도 진단 등 AI 분석에 데이터를 활용합니다.",
      "동의하지 않으면 일부 AI 기능이 제한되며, 저장 자체는 가능합니다.",
      "철회는 삭제가 아니라 감사 가능한 이력으로 남깁니다.",
    ],
  },
  {
    code: "MARKETING",
    title: "마케팅 정보 수신",
    version: "v1.0",
    required: false,
    effectiveDate: "2026-01-01",
    body: [
      "신규 기능·이벤트·혜택 안내를 이메일/푸시로 받습니다.",
      "언제든지 설정에서 수신을 해제할 수 있습니다.",
    ],
  },
];

export function findConsentTerm(code: ConsentTerm["code"]): ConsentTerm {
  const found = consentTerms.find((t) => t.code === code);
  if (found) return found;
  // 알 수 없는 코드는 빈 메타로 안전 폴백(렌더 깨짐 방지).
  return { code, title: code, version: "", required: false, effectiveDate: "", body: [] };
}
