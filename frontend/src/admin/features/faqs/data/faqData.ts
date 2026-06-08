export type FaqCategory = "일반" | "계정" | "결제" | "AI기능" | "면접";

export interface Faq {
  id: number;
  cat: FaqCategory;
  q: string;
  a: string;
  on: boolean;
  images: string[];
  yt: string;
}

export const FAQ_CATEGORIES: FaqCategory[] = ["일반", "계정", "결제", "AI기능", "면접"];

/** 카테고리 → ct-shared 컬러키 매핑 */
export const CAT_COLOR: Record<FaqCategory, string> = {
  "일반": "free",
  "계정": "job",
  "결제": "role",
  "AI기능": "interview",
  "면접": "portfolio",
};

export const FAQS: Faq[] = [
  {
    id: 1, cat: "AI기능", on: true,
    q: "이력서 분석은 하루에 몇 번까지 가능한가요?",
    a: "무료 회원은 월 3회, 프로 플랜은 무제한으로 이력서 분석을 이용할 수 있어요. 사용량은 마이페이지에서 확인할 수 있습니다.",
    images: [], yt: "",
  },
  {
    id: 2, cat: "결제", on: true,
    q: "결제 후 환불은 어떻게 받나요?",
    a: "결제일로부터 7일 이내, AI 기능을 사용하지 않은 경우 전액 환불이 가능합니다. 고객센터 문의하기로 접수해 주세요.",
    images: [], yt: "",
  },
  {
    id: 3, cat: "계정", on: true,
    q: "비밀번호를 잊어버렸어요.",
    a: "로그인 화면의 '비밀번호 찾기'를 누르면 가입한 이메일로 재설정 링크를 보내드립니다.",
    images: [], yt: "",
  },
  {
    id: 4, cat: "AI기능", on: true,
    q: "모의면접 음성 데이터는 어떻게 보관되나요?",
    a: "음성 데이터는 분석 완료 후 6개월 이내에 파기되며, 동의하지 않으면 AI 학습에 사용되지 않습니다.",
    images: [], yt: "",
  },
  {
    id: 5, cat: "면접", on: true,
    q: "AI 가상 면접은 어떤 유형이 있나요?",
    a: "직무 면접, 인성 면접, 실전 면접, 음성 면접 네 가지 유형을 제공합니다. 직무를 먼저 지정하면 맞춤 질문이 생성돼요.",
    images: [], yt: "",
  },
  {
    id: 6, cat: "일반", on: true,
    q: "서비스 이용 시간에 제한이 있나요?",
    a: "연중무휴 24시간 이용할 수 있습니다. 정기 점검 시에는 사전 공지 후 일시적으로 제한될 수 있어요.",
    images: [], yt: "",
  },
  {
    id: 7, cat: "계정", on: false,
    q: "회원 탈퇴하면 데이터는 어떻게 되나요?",
    a: "탈퇴 시 개인정보는 지체 없이 파기됩니다. 단, 법령상 보존이 필요한 결제 기록 등은 일정 기간 보관됩니다.",
    images: [], yt: "",
  },
];
