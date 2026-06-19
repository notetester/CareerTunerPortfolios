export interface ConsentTerm {
  type: "TERMS" | "PRIVACY" | "AI_DATA" | "MARKETING";
  title: string;
  version: string;
  effectiveDate: string;
  required: boolean;
  body: string[];
}

export const consentTerms: ConsentTerm[] = [
  {
    type: "TERMS",
    title: "서비스 이용약관",
    version: "v2026.06",
    effectiveDate: "2026-06-01",
    required: true,
    body: [
      "CareerTuner는 지원 건 기반의 취업 준비, 프로필 관리, AI 분석 보조 기능을 제공합니다.",
      "사용자는 본인의 이력, 자기소개, 지원 정보를 정확하게 입력해야 하며 타인의 정보를 무단으로 등록할 수 없습니다.",
      "AI 분석 결과는 취업 준비를 돕는 참고 자료이며 최종 지원 판단과 제출 책임은 사용자에게 있습니다.",
    ],
  },
  {
    type: "PRIVACY",
    title: "개인정보 처리방침",
    version: "v2026.06",
    effectiveDate: "2026-06-01",
    required: true,
    body: [
      "회원 식별, 로그인, 지원 건 관리, 프로필 분석을 위해 이메일, 이름, 프로필, 이력서 원문 등을 처리합니다.",
      "개인정보는 서비스 제공과 보안 감사 목적에 필요한 범위에서만 사용하며, 관련 법령과 내부 보관 기준에 따라 관리합니다.",
      "사용자는 개인정보 열람, 정정, 삭제, 처리 제한을 요청할 수 있습니다.",
    ],
  },
  {
    type: "AI_DATA",
    title: "AI 데이터 사용 동의",
    version: "v2026.06",
    effectiveDate: "2026-06-01",
    required: false,
    body: [
      "프로필 요약, 직무 역량 추출, 완성도 진단 등 AI 기능 제공을 위해 사용자가 입력한 프로필과 이력 정보를 분석합니다.",
      "동의를 철회하면 프로필 저장은 가능하지만 AI 요약, 역량 추출, 완성도 진단 기능은 제한됩니다.",
      "AI 결과는 자동 생성된 참고 정보이므로 사용자가 확인한 뒤 활용해야 합니다.",
    ],
  },
  {
    type: "MARKETING",
    title: "마케팅 정보 수신 동의",
    version: "v2026.06",
    effectiveDate: "2026-06-01",
    required: false,
    body: [
      "이벤트, 신규 기능, 취업 준비 콘텐츠 안내를 이메일 또는 서비스 알림으로 받을 수 있습니다.",
      "마케팅 수신 동의는 선택 사항이며, 동의하지 않아도 기본 서비스 이용은 가능합니다.",
      "사용자는 언제든지 설정 화면에서 수신 동의를 변경할 수 있습니다.",
    ],
  },
];

export function findConsentTerm(type: ConsentTerm["type"]): ConsentTerm {
  const term = consentTerms.find((item) => item.type === type);
  if (!term) throw new Error(`Unknown consent term: ${type}`);
  return term;
}
