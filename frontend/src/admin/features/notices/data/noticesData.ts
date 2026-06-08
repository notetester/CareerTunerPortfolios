export type NoticeStatus = "published" | "draft" | "scheduled";

export interface Notice {
  id: number;
  title: string;
  status: NoticeStatus;
  pinned: boolean;
  date: string;
  views: number;
  body: string;
  cover: string | null;
  images: string[];
}

export const NOTICES: Notice[] = [
  {
    id: 1,
    title: "[중요] 개인정보처리방침 개정 안내 (6/20 시행)",
    status: "published",
    pinned: true,
    date: "2026.06.02",
    views: 3142,
    body: "안녕하세요, CareerTuner입니다.\n\n개인정보처리방침이 2026년 6월 20일자로 개정됩니다. 주요 변경 사항은 모의면접 음성 데이터의 보관 기간 명시와 제3자 제공 항목 정비입니다.",
    cover: null,
    images: [],
  },
  {
    id: 2,
    title: "음성 답변 분석 기능 정식 출시",
    status: "published",
    pinned: true,
    date: "2026.05.28",
    views: 5870,
    body: "모의면접에서 음성으로 답변하면 말 속도·군더더기 표현·답변 구조까지 분석해 드립니다. 지금 바로 사용해보세요.",
    cover: null,
    images: [],
  },
  {
    id: 3,
    title: "5월 정기 점검 안내 (5/30 02:00~04:00)",
    status: "published",
    pinned: false,
    date: "2026.05.25",
    views: 2204,
    body: "서비스 안정화를 위한 정기 점검이 진행됩니다. 점검 시간 동안 일부 기능 이용이 제한될 수 있습니다.",
    cover: null,
    images: [],
  },
  {
    id: 4,
    title: "프로 플랜 크레딧 정책 변경 예정",
    status: "scheduled",
    pinned: false,
    date: "2026.06.10 예약",
    views: 0,
    body: "프로 플랜의 월 AI 크레딧이 확대됩니다. 자세한 내용은 게시 예정일에 공개됩니다.",
    cover: null,
    images: [],
  },
  {
    id: 5,
    title: "면접 코칭 코치진 확대 (백엔드·데이터·디자인)",
    status: "draft",
    pinned: false,
    date: "임시저장",
    views: 0,
    body: "1:1 면접 코칭 코치진이 확대되었습니다. (작성 중)",
    cover: null,
    images: [],
  },
];
