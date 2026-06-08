export interface Report {
  id: number;
  reason: string;
  type: "게시글" | "댓글";
  cnt: number;
  title: string;
  excerpt: string;
  cat: string;
  catKey: string;
  author: string;
  time: string;
  status: "pending" | "resolved";
  action?: string;
  reasons: { l: string; n: number }[];
}

export const REPORTS: Report[] = [
  {
    id: 1, reason: "스팸/광고", type: "게시글", cnt: 5,
    title: "★★★ 취업 컨설팅 100% 합격 보장 ★★★",
    excerpt: "지금 바로 연락주세요. 카톡 ID: jobpass2026 / 단돈 99,000원에 대기업 합격까지 책임집니다. 외부 링크 클릭...",
    cat: "자유게시판", catKey: "free", author: "익명_4821", time: "12분 전",
    status: "pending", reasons: [{ l: "스팸/광고", n: 5 }, { l: "허위 정보", n: 2 }],
  },
  {
    id: 2, reason: "욕설/혐오 표현", type: "댓글", cnt: 4,
    title: "댓글: \"이런 회사를 왜 가냐 ...\"",
    excerpt: "면접 후기 글에 달린 댓글. 특정 기업과 작성자를 비하하는 표현이 다수 포함되어 신고가 누적되었습니다.",
    cat: "면접후기", catKey: "interview", author: "익명_1043", time: "38분 전",
    status: "pending", reasons: [{ l: "욕설/혐오", n: 4 }],
  },
  {
    id: 3, reason: "개인정보 노출", type: "게시글", cnt: 3,
    title: "○○전자 최종 면접 후기 (면접관 실명 포함)",
    excerpt: "면접관 이름과 연락처가 본문에 그대로 적혀 있습니다. 개인정보 보호를 위해 검토가 필요합니다.",
    cat: "면접후기", catKey: "interview", author: "익명_2299", time: "1시간 전",
    status: "pending", reasons: [{ l: "개인정보", n: 3 }],
  },
  {
    id: 4, reason: "허위 정보", type: "게시글", cnt: 2,
    title: "이 회사 곧 망합니다 (내부자 정보)",
    excerpt: "근거 없는 폐업설을 사실처럼 단정해 게시. 확인되지 않은 정보로 신고가 접수되었습니다.",
    cat: "취업후기", catKey: "job", author: "익명_7741", time: "2시간 전",
    status: "pending", reasons: [{ l: "허위 정보", n: 2 }],
  },
  {
    id: 5, reason: "스팸/광고", type: "댓글", cnt: 2,
    title: "댓글: \"제 블로그에 자소서 템플릿 ...\"",
    excerpt: "여러 게시글에 동일한 외부 블로그 링크를 반복 게시한 댓글입니다.",
    cat: "합격전략", catKey: "pass", author: "익명_5526", time: "3시간 전",
    status: "resolved", action: "숨김 처리됨", reasons: [{ l: "스팸/광고", n: 2 }],
  },
  {
    id: 6, reason: "욕설/혐오 표현", type: "게시글", cnt: 1,
    title: "스터디원 구합니다 (특정 성별 비하 표현)",
    excerpt: "모집 글 본문에 부적절한 표현이 포함되어 신고되었습니다.",
    cat: "자유게시판", catKey: "free", author: "익명_8810", time: "5시간 전",
    status: "resolved", action: "반려됨", reasons: [{ l: "욕설/혐오", n: 1 }],
  },
];
