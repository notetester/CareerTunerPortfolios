import type { CommunityPost, CommunityComment } from "../types/community";

export const mockPosts: CommunityPost[] = [
  {
    id: 1, category: "interview-review", categoryLabel: "면접후기",
    companyName: "네이버", jobRole: "백엔드 신입", result: "최종합격",
    title: "네이버 백엔드 신입 공채 면접 후기 (코테 + 2차)",
    content: "1차는 코딩테스트 리뷰 위주였고, 2차에서는 시스템 설계와 협업 경험을 깊게 물어봤어요. 분위기는 생각보다 편안했고, 압박 질문은 거의 없었습니다.",
    tags: ["네이버", "백엔드", "신입공채"],
    author: { id: 1, name: "익명", isAnonymous: true },
    stats: { viewCount: 1204, commentCount: 32, likeCount: 124, bookmarkCount: 18 },
    status: "PUBLISHED", createdAt: "15분 전", isHot: true, daysAgo: 0,
  },
  {
    id: 2, category: "job-review", categoryLabel: "취업후기",
    title: "취준 2년 6개월차, 드디어 금융권 합격했습니다",
    content: "오래 걸렸지만 포기 안 하길 잘했어요. 자소서는 두괄식으로, 면접은 결국 직무 이해도가 핵심이더라고요.",
    tags: ["금융권", "취준"],
    author: { id: 2, name: "익명", isAnonymous: true },
    stats: { viewCount: 8265, commentCount: 41, likeCount: 88, bookmarkCount: 22 },
    status: "PUBLISHED", createdAt: "1시간 전", isHot: true, daysAgo: 0,
  },
  {
    id: 3, category: "portfolio-feedback", categoryLabel: "포트폴리오",
    companyName: "카카오", jobRole: "프론트엔드", result: "1차합격",
    title: "카카오 프론트 지원 — 포트폴리오 이렇게 구성했어요",
    content: "프로젝트 3개를 문제-해결-성과 순서로 정리했더니 서류 통과율이 확 올랐어요. 링크는 노션으로 깔끔하게 정리했습니다.",
    tags: ["카카오", "포트폴리오"],
    author: { id: 3, name: "익명", isAnonymous: true },
    stats: { viewCount: 3120, commentCount: 12, likeCount: 56, bookmarkCount: 8 },
    status: "PUBLISHED", createdAt: "2시간 전", daysAgo: 0,
  },
  {
    id: 4, category: "job-question", categoryLabel: "직무질문",
    title: "데이터 분석가 직무, 비전공자도 가능할까요?",
    content: "통계 전공이 아닌데 SQL이랑 파이썬 공부 중입니다. 현직자분들 조언 부탁드려요.",
    tags: ["데이터분석", "비전공"],
    author: { id: 4, name: "익명", isAnonymous: true },
    stats: { viewCount: 7730, commentCount: 13, likeCount: 12, bookmarkCount: 5 },
    status: "PUBLISHED", createdAt: "3시간 전", daysAgo: 2,
  },
  {
    id: 5, category: "success-strategy", categoryLabel: "합격전략",
    title: "이력서 분석 리포트대로 고쳤더니 서류 통과율이 올랐어요",
    content: "AI가 짚어준 보완점대로 고쳤더니 서류 통과율이 확 올랐어요. 무료 3회로도 충분히 감 잡힙니다.",
    tags: ["이력서", "서류"],
    author: { id: 5, name: "익명", isAnonymous: true },
    stats: { viewCount: 5504, commentCount: 8, likeCount: 204, bookmarkCount: 31 },
    status: "PUBLISHED", createdAt: "5시간 전", daysAgo: 5,
  },
  {
    id: 6, category: "certificate-review", categoryLabel: "자격증후기",
    title: "SQLD 2주 벼락치기 합격 후기 (비전공)",
    content: "기출 3회분만 제대로 돌리면 충분했어요. 2과목 비중이 높으니 거기에 시간을 더 쓰세요.",
    tags: ["SQLD", "자격증"],
    author: { id: 6, name: "익명", isAnonymous: true },
    stats: { viewCount: 4410, commentCount: 19, likeCount: 71, bookmarkCount: 12 },
    status: "PUBLISHED", createdAt: "7시간 전", daysAgo: 14,
  },
];

export const mockHotPosts = [
  { title: "백수 3개월 차, 이대로 괜찮을까요?", comments: 37, views: 5504 },
  { title: "연봉 협상 이렇게 했더니 500 올렸습니다", comments: 41, views: 8265 },
  { title: "중고 신입으로 대기업 가는 현실적인 방법", comments: 13, views: 7730 },
  { title: "면접 때 역질문 뭐 하셨어요?", comments: 22, views: 4210 },
];

export const mockPostDetail = {
  category: "면접후기" as const,
  title: "쿠팡 백엔드 개발자 경력 면접 후기 (직무 → 임원, 최종합격)",
  author: "익명",
  authorRole: "백엔드 4년차",
  time: "2026.05.30 · 오후 3:20",
  views: 3482,
  result: "최종합격",
  likes: 124,
  bookmarks: 38,
  meta: {
    company: "쿠팡 (Coupang)",
    position: "백엔드 개발자 · 경력",
    type: "대면 면접",
    date: "2026.05.28",
    stage: "1차 직무 → 2차 임원",
    difficulty: 4 as const,
  },
  body: `## 지원 배경

현재 4년차 백엔드 개발자이고, **대용량 트래픽 경험**을 더 쌓고 싶어 쿠팡에 지원했습니다. 서류 → 코딩테스트 → 1차 직무 면접 → 2차 임원 면접 순으로 진행됐고, 전체 일정은 약 3주 걸렸어요.

## 전형별 후기

### 1차 — 직무 면접 (약 70분)

가장 깊게 본 라운드였습니다. 이력서에 적은 프로젝트를 **하나하나 파고드는 방식**이라, 본인이 직접 한 일과 의사결정 근거를 명확히 정리해 가는 게 중요해요.

- 최근 프로젝트에서 가장 어려웠던 기술적 의사결정과 그 이유
- 트래픽이 10배로 늘면 현재 구조를 어떻게 바꿀 것인가
- 장애 상황을 가정한 디버깅 시나리오

라이브 코딩도 있었는데, 정답보다 **사고 과정과 커뮤니케이션**을 봤습니다. 예를 들면 이런 문제였어요:

\`\`\`java
// 주문 로그에서 동일 유저의 5분 이내 중복 결제를 찾기
List<Order> findDuplicatePayments(List<Order> orders) {
    // 시간순 정렬 후 슬라이딩 윈도우로 탐색
    // 핵심: 시간 복잡도와 동시성 고려를 '말로' 설명하기
}
\`\`\`

### 2차 — 임원 면접 (약 40분)

기술보다 **컬처핏과 성장 방향**에 가까웠어요. 압박은 거의 없었고 편안한 대화에 가까웠습니다.

1. 5년 뒤 어떤 개발자가 되고 싶은가
2. 협업에서 갈등이 생겼을 때 어떻게 풀어왔는가
3. 우리 서비스에서 개선하고 싶은 점 한 가지

## 준비하면서 도움 됐던 것

> 직무 면접은 "내 이력서를 면접관보다 내가 더 잘 안다"는 상태로 가는 게 핵심입니다.

- 프로젝트별로 **문제 → 해결 → 성과(수치)** 한 장 요약 만들기
- 예상 꼬리질문을 미리 적어두고 소리 내어 답변 연습하기
- \`CareerTuner\` 모의면접으로 직무 질문 2회 돌려보기 — 실제로 비슷한 질문이 나왔어요

## 결과

최종 합격했습니다. 준비하시는 분들 모두 좋은 결과 있으시길 바라요!`,
  comments: [
    { name: "백엔드 3년차", time: "2시간 전", likes: 18, isAuthor: false, text: "라이브 코딩에서 사고 과정 본다는 거 진짜 공감돼요. 정답 틀려도 계속 설명하니까 오히려 분위기 풀리더라고요." },
    { name: "익명", time: "1시간 전", likes: 7, isAuthor: false, text: "2차 임원 면접 질문 미리 정리해주셔서 감사합니다. 다음 주에 보는데 큰 도움 됐어요!" },
    { name: "작성자", time: "50분 전", likes: 5, isAuthor: true, text: "@익명 화이팅이에요! 임원 면접은 솔직하게 본인 생각 말하는 게 제일 좋았던 것 같아요." },
    { name: "취준생", time: "30분 전", likes: 2, isAuthor: false, text: "모의면접 2회로도 효과 있었나요? 무료로 해볼까 고민 중이라서 여쭤봐요." },
    { name: "프론트 5년차", time: "12분 전", likes: 0, isAuthor: false, text: "문제-해결-성과 한 장 요약 팁 가져갑니다. 좋은 후기 감사합니다!" },
  ],
};
