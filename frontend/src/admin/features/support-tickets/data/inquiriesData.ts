export type InquiryStatus = "pending" | "progress" | "hold" | "answered" | "closed";

export interface InquiryAttachment {
  id: number;
  name: string;
  size: number;
}

export interface InquiryMessage {
  who: "user" | "admin";
  name: string;
  time: string;
  text: string;
  attachments?: InquiryAttachment[];
}

export interface Inquiry {
  id: number;
  cat: string;
  title: string;
  member: string;
  date: string;
  status: InquiryStatus;
  assignee: string;
  priority: boolean;
  plan: string;
  joined: string;
  lastPay: string;
  memo: string;
  msgs: InquiryMessage[];
}

export const TEMPLATES = [
  { label: "확인 중 안내", text: "안녕하세요, CareerTuner입니다. 문의 주신 내용을 확인하고 있으며, 영업일 기준 1일 이내에 답변드리겠습니다." },
  { label: "환불/크레딧", text: "확인 결과 결제는 정상 처리되었습니다. 크레딧은 5분 내 자동 반영되며, 반영되지 않으면 즉시 수동으로 충전해 드리겠습니다." },
  { label: "인증메일 재발송", text: "인증 메일을 재발송했습니다. 메일이 보이지 않으면 스팸함을 확인해 주시고, 그래도 수신되지 않으면 다시 알려주세요." },
  { label: "언어 설정", text: "마이페이지 > 설정 > 언어에서 한국어로 변경하시면 분석 리포트가 한글로 제공됩니다." },
];

export const ASSIGNEES = ["미지정", "김운영", "이수민", "박지호"];

export const INQUIRIES: Inquiry[] = [
  {
    id: 1, cat: "결제", title: "프로 결제했는데 크레딧이 안 들어와요",
    member: "정도현", date: "2026.06.07 08:55",
    status: "pending", assignee: "미지정", priority: true,
    plan: "프로", joined: "2025.12.30", lastPay: "프로 월간 ₩19,900 · 오늘", memo: "",
    msgs: [
      { who: "user", name: "정도현", time: "08:55", text: "오늘 오전에 프로 월간 결제를 했는데, 마이페이지에 AI 크레딧이 충전되지 않았습니다. 결제는 정상적으로 완료됐다고 떠요." },
    ],
  },
  {
    id: 2, cat: "AI기능", title: "이력서 분석 결과가 영어로 나옵니다",
    member: "한소희", date: "2026.06.07 01:20",
    status: "progress", assignee: "이수민", priority: false,
    plan: "무료", joined: "2026.02.11", lastPay: "결제 내역 없음",
    memo: "언어 설정 이슈로 추정. 재현 확인 중.",
    msgs: [
      { who: "user", name: "한소희", time: "어제 01:20", text: "한글 이력서를 올렸는데 분석 리포트 일부가 영어로 나와요. 언어 설정을 바꿔야 하나요?" },
    ],
  },
  {
    id: 3, cat: "계정", title: "이메일 인증 메일이 안 와요",
    member: "박준영", date: "2026.06.06 22:10",
    status: "pending", assignee: "미지정", priority: false,
    plan: "무료", joined: "2026.06.06", lastPay: "결제 내역 없음", memo: "",
    msgs: [
      { who: "user", name: "박준영", time: "어제 22:10", text: "회원가입 후 인증 메일을 받지 못했습니다. 스팸함도 확인했는데 없어요." },
    ],
  },
  {
    id: 4, cat: "기술문제", title: "모의면접 중 화면이 멈춰요",
    member: "이서연", date: "2026.06.06 15:30",
    status: "answered", assignee: "김운영", priority: false,
    plan: "베이직", joined: "2026.01.15", lastPay: "베이직 월간 ₩9,900 · 05.15",
    memo: "크롬 캐시 이슈로 안내 완료. 재발 시 기기정보 요청.",
    msgs: [
      { who: "user", name: "이서연", time: "06.06 15:30", text: "음성 면접을 진행하는 중에 화면이 멈추고 마이크가 끊깁니다. 크롬 최신 버전 사용 중이에요." },
      { who: "admin", name: "김운영", time: "06.06 16:12", text: "안녕하세요, 불편을 드려 죄송합니다. 마이크 권한을 재설정하시고 브라우저 캐시를 비운 뒤 다시 시도해 주세요. 동일 증상이 계속되면 사용 기기 정보를 알려주시면 더 살펴보겠습니다." },
    ],
  },
  {
    id: 5, cat: "기타", title: "제휴 문의 드립니다",
    member: "최민호", date: "2026.06.05 11:00",
    status: "answered", assignee: "박지호", priority: false,
    plan: "무료", joined: "2025.05.20", lastPay: "결제 내역 없음",
    memo: "제휴팀(박지호) 전달 완료.",
    msgs: [
      { who: "user", name: "최민호", time: "06.05 11:00", text: "대학 취업지원센터와의 제휴를 문의드리고 싶습니다. 담당자 연결 부탁드려요." },
      { who: "admin", name: "김운영", time: "06.05 13:40", text: "관심 가져주셔서 감사합니다. 제휴팀에 전달했으며 영업일 기준 2일 이내에 redacted-7bf5be73595323c7@example.com로 연락드리겠습니다." },
    ],
  },
];
