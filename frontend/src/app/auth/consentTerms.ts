/**
 * 동의 토글에 표시하는 약관 요약 메타(설정·소셜 가입 공용).
 * 여기 body는 화면 표시용 요약이며, 구속력 있는 전문은 /legal 페이지가 기준이다.
 */
export interface ConsentTerm {
  code: "TERMS" | "PRIVACY" | "AI_DATA" | "RESUME_ANALYSIS" | "MARKETING";
  title: string;
  version: string;
  required: boolean;
  effectiveDate: string;
  documentHref: string;
  restriction: string;
  body: string[];
}

export const consentTerms: ConsentTerm[] = [
  {
    code: "TERMS",
    title: "서비스 이용약관",
    version: "v2026.07",
    required: true,
    effectiveDate: "2026-07-10",
    documentHref: "/legal/terms",
    restriction: "철회하면 다시 동의할 때까지 회원 전용 서비스를 이용할 수 없습니다.",
    body: [
      "CareerTuner는 지원 건 기반의 취업 준비, 프로필 관리, AI 분석 보조 기능을 제공합니다.",
      "사용자는 본인의 이력, 자기소개, 지원 정보를 정확하게 입력해야 하며 타인의 정보를 무단으로 등록할 수 없습니다.",
      "AI 분석 결과는 취업 준비를 돕는 참고 자료이며 최종 지원 판단과 제출 책임은 사용자에게 있습니다.",
    ],
  },
  {
    code: "PRIVACY",
    title: "개인정보 처리방침",
    version: "v2026.07",
    required: true,
    effectiveDate: "2026-07-10",
    documentHref: "/legal/privacy",
    restriction: "철회하면 다시 동의할 때까지 개인정보를 사용하는 회원 기능이 중단됩니다.",
    body: [
      "회원 식별, 로그인, 지원 건 관리, 프로필 분석을 위해 이메일, 이름, 프로필, 이력서 원문 등을 처리합니다.",
      "개인정보는 서비스 제공과 보안 감사 목적에 필요한 범위에서만 사용하며, 관련 법령과 내부 보관 기준에 따라 관리합니다.",
      "사용자는 개인정보 열람, 정정, 삭제, 처리 제한을 요청할 수 있습니다.",
    ],
  },
  {
    code: "AI_DATA",
    title: "AI 데이터 사용 동의",
    version: "v2026.07",
    required: false,
    effectiveDate: "2026-07-10",
    documentHref: "/legal/ai-data-consent",
    restriction: "철회하면 AI 분석, 면접 평가, 첨삭과 자동 준비 기능이 중단됩니다.",
    body: [
      "프로필 요약, 직무 역량 추출, 완성도 진단 등 AI 기능 제공을 위해 사용자가 입력한 프로필과 이력 정보를 분석합니다.",
      "동의를 철회하면 프로필 저장은 가능하지만 AI 요약, 역량 추출, 완성도 진단 기능은 제한됩니다.",
      "AI 결과는 자동 생성된 참고 정보이므로 사용자가 확인한 뒤 활용해야 합니다.",
    ],
  },
  {
    code: "RESUME_ANALYSIS",
    title: "이력서 분석 개인정보 수집·이용 동의",
    version: "v2026.07",
    required: false,
    effectiveDate: "2026-07-10",
    documentHref: "/legal/resume-analysis-consent",
    restriction: "철회하면 이력서 가져오기와 이력서·프로필 기반 AI 분석이 중단됩니다.",
    body: [
      "이력서 파일과 프로필 원문에 포함된 학력, 경력, 프로젝트, 기술, 자격 정보를 구조화하고 분석합니다.",
      "동의하지 않아도 계정과 수동 프로필 편집은 사용할 수 있지만 이력서 가져오기와 분석 기능은 제한됩니다.",
      "사용자는 설정에서 언제든지 철회하거나 다시 동의할 수 있습니다.",
    ],
  },
  {
    code: "MARKETING",
    title: "마케팅 정보 수신 동의",
    version: "v2026.07",
    required: false,
    effectiveDate: "2026-07-10",
    documentHref: "/legal/marketing",
    restriction: "철회해도 기본 서비스 이용에는 영향이 없으며 새 마케팅 안내만 중단됩니다.",
    body: [
      "이벤트, 신규 기능, 취업 준비 콘텐츠 안내를 이메일 또는 서비스 알림으로 받을 수 있습니다.",
      "마케팅 수신 동의는 선택 사항이며, 동의하지 않아도 기본 서비스 이용은 가능합니다.",
      "사용자는 언제든지 설정 화면에서 수신 동의를 변경할 수 있습니다.",
    ],
  },
];

export function findConsentTerm(code: ConsentTerm["code"]): ConsentTerm {
  const found = consentTerms.find((term) => term.code === code);
  if (found) return found;
  return {
    code,
    title: code,
    version: "",
    required: false,
    effectiveDate: "",
    documentHref: "/settings",
    restriction: "동의 상태를 설정에서 확인해 주세요.",
    body: [],
  };
}
