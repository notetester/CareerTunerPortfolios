// 데모/목: C 도메인 커뮤니티(게시판/면접후기/댓글/반응/신고/가이드라인).
// 일관 페르소나: "김데모"(id 9001). 기업/직무는 지원 건(101 카카오·102 네이버·103 토스·104 라인)과 맞춘다.
// communityApi.ts 의 내부 BackendPost/PostPageData 는 export 되지 않아 동일 필드로 로컬 미러를 둔다.
import type { MockRoute, MockContext } from "../registry";
import { ok, iso, pageOf } from "../registry";
import type { CommunityComment, InterviewReviewMetadata } from "@/features/community/types/community";
import type { AiTagResult } from "@/features/community/api/communityApi";
import type { GuidelineData, GuidelineRule, GuidelineParams } from "@/features/community/api/guidelineApi";

/* ── communityApi 내부 응답 타입 미러 (필드 정확히 일치) ── */
interface BackendPost {
  id: number;
  category: string; // 백엔드 enum (예: "INTERVIEW_REVIEW")
  categoryLabel: string;
  title: string;
  content: string;
  tags: string[];
  author: { id: number; name: string; isAnonymous: boolean };
  stats: { viewCount: number; commentCount: number; likeCount: number; bookmarkCount: number };
  status: string;
  createdAt: string;
  updatedAt?: string;
  companyName?: string;
  jobRole?: string;
  interviewReview?: InterviewReviewMetadata;
  liked?: boolean;
  bookmarked?: boolean;
}

interface PostPageData {
  posts: BackendPost[];
  total: number;
  page: number;
  size: number;
}

const DEMO_AUTHOR = { id: 9001, name: "김데모", isAnonymous: false };
const ANON = (name: string) => ({ id: 0, name, isAnonymous: true });

/* ── 게시글 데이터 (카테고리 다양화 + 면접후기 1건) ── */
const POSTS: BackendPost[] = [
  {
    id: 5101,
    category: "INTERVIEW_REVIEW",
    categoryLabel: "면접후기",
    title: "카카오 프론트엔드 1차 기술면접 후기 (React 상태관리 집중 질문)",
    content:
      "## 전형 흐름\n서류 → 코딩테스트 → 1차 기술면접 순으로 진행됐어요.\n\n## 분위기\n면접관 2분 모두 편하게 대해주셔서 긴장이 금방 풀렸습니다. 압박은 거의 없었어요.\n\n## 기억나는 질문\n- **React 리렌더링 최적화**를 실제로 어떻게 했는지\n- `useMemo`/`useCallback` 남용의 단점\n- 전역 상태 관리 도구 선택 기준 (Redux vs Zustand)\n- REST API 에러 처리 전략\n\n## 느낀 점\n프로젝트에서 **왜** 그 선택을 했는지 꼬리질문이 깊게 들어와요. 코드만 외우지 말고 의사결정 근거를 정리해 가세요.",
    tags: ["카카오", "프론트엔드", "기술면접", "React"],
    author: ANON("취준생A"),
    stats: { viewCount: 3284, commentCount: 3, likeCount: 142, bookmarkCount: 58 },
    status: "PUBLISHED",
    createdAt: iso(2),
    companyName: "카카오",
    jobRole: "프론트엔드 개발자",
    interviewReview: {
      companyName: "카카오",
      jobRole: "프론트엔드 개발자",
      interviewType: "1차 기술면접",
      difficulty: 4,
      interviewDate: "2026-06-10",
      resultStatus: "PASSED",
      stage: "1차",
      questions: [
        "React 리렌더링을 어떻게 최적화했나요?",
        "useMemo와 useCallback을 남용하면 어떤 문제가 있나요?",
        "전역 상태 관리 도구는 어떤 기준으로 선택하나요?",
        "REST API 호출 시 에러는 어떻게 처리하나요?",
      ],
    },
    liked: false,
    bookmarked: true,
  },
  {
    id: 5102,
    category: "JOB_REVIEW",
    categoryLabel: "취업후기",
    title: "네이버 프론트엔드 최종 합격 후기 — 6개월 준비 회고",
    content:
      "## 준비 기간\n총 6개월. 처음 3개월은 React/TypeScript 기초, 나머지는 프로젝트와 면접 준비에 썼어요.\n\n## 도움 됐던 것\n- 작은 프로젝트라도 **배포까지** 끝내기 (AWS S3 + CloudFront)\n- 성능 개선 경험을 **수치로** 정리 (초기 로딩 35% 단축)\n- 면접 답변을 STAR 구조로 미리 써보기\n\n준비하시는 분들 모두 화이팅입니다!",
    tags: ["네이버", "최종합격", "회고", "TypeScript"],
    author: ANON("합격수기"),
    stats: { viewCount: 5120, commentCount: 2, likeCount: 268, bookmarkCount: 121 },
    status: "PUBLISHED",
    createdAt: iso(5),
    companyName: "네이버",
    jobRole: "프론트엔드 개발자",
    liked: true,
    bookmarked: false,
  },
  {
    id: 5103,
    category: "JOB_QUESTION",
    categoryLabel: "직무질문",
    title: "프론트엔드 신입, 포트폴리오에 토이프로젝트 몇 개가 적당할까요?",
    content:
      "신입 프론트엔드 지원 준비 중입니다. 작은 토이프로젝트를 여러 개 두는 게 좋을까요, 아니면 완성도 높은 프로젝트 1~2개에 집중하는 게 좋을까요? 현직자분들 의견이 궁금합니다.",
    tags: ["신입", "포트폴리오", "프론트엔드"],
    author: DEMO_AUTHOR,
    stats: { viewCount: 842, commentCount: 1, likeCount: 17, bookmarkCount: 4 },
    status: "PUBLISHED",
    createdAt: iso(1),
    liked: false,
    bookmarked: false,
  },
  {
    id: 5104,
    category: "SUCCESS_STRATEGY",
    categoryLabel: "합격전략",
    title: "토스 코딩테스트 통과 전략 — 자료구조보다 구현력",
    content:
      "## 핵심\n토스 코테는 화려한 알고리즘보다 **정확한 구현**과 엣지케이스 처리를 봅니다.\n\n## 추천 학습\n1. 문자열/배열 구현 문제 반복\n2. 시간복잡도 계산 습관화\n3. 제출 전 엣지케이스 점검 리스트 만들기\n\n> 어려운 문제 1개보다 쉬운 문제 무실수가 더 안전합니다.",
    tags: ["토스", "코딩테스트", "합격전략"],
    author: ANON("코테장인"),
    stats: { viewCount: 2190, commentCount: 0, likeCount: 88, bookmarkCount: 41 },
    status: "PUBLISHED",
    createdAt: iso(8),
    companyName: "토스",
    jobRole: "웹 프론트엔드 엔지니어",
    liked: false,
    bookmarked: false,
  },
  {
    id: 5105,
    category: "FREE",
    categoryLabel: "자유게시판",
    title: "라인 면접 보고 오신 분 계신가요? 결과 대기 너무 떨려요",
    content: "오늘 라인 프론트엔드 2차 면접 보고 왔습니다. 결과 기다리는 동안 다들 어떻게 멘탈 관리하시나요? 너무 떨리네요 ㅠㅠ",
    tags: ["라인", "면접후기", "잡담"],
    author: ANON("긴장한지원자"),
    stats: { viewCount: 631, commentCount: 0, likeCount: 23, bookmarkCount: 2 },
    status: "PUBLISHED",
    createdAt: iso(0),
    companyName: "라인",
    jobRole: "프론트엔드 개발자",
    liked: false,
    bookmarked: false,
  },
];

const POST_BY_ID = new Map<number, BackendPost>(POSTS.map((p) => [p.id, p]));

/* ── 댓글 (게시글별) ── */
const COMMENTS: Record<number, CommunityComment[]> = {
  5101: [
    { id: 6011, postId: 5101, author: ANON("준비생B"), content: "꼬리질문 깊게 들어온다는 게 진짜 공감돼요. 의사결정 근거 정리 꿀팁 감사합니다!", likeCount: 12, isAuthor: false, createdAt: iso(2), liked: false },
    { id: 6012, postId: 5101, author: DEMO_AUTHOR, content: "useMemo 남용 단점은 어떻게 답하셨는지 궁금합니다.", likeCount: 4, isAuthor: false, createdAt: iso(1), liked: false },
    { id: 6013, postId: 5101, author: ANON("취준생A"), content: "@김데모 의존성 배열 관리 비용과 메모리 부담을 언급했어요. 측정 없이 최적화하지 말라는 톤으로요.", likeCount: 8, isAuthor: true, createdAt: iso(1), liked: true },
  ],
  5102: [
    { id: 6021, postId: 5102, author: ANON("취준3년차"), content: "배포까지 끝내라는 말 너무 와닿네요. 저도 S3 배포부터 다시 해봐야겠어요.", likeCount: 19, isAuthor: false, createdAt: iso(4), liked: false },
    { id: 6022, postId: 5102, author: DEMO_AUTHOR, content: "성능 35% 단축은 어떤 도구로 측정하셨나요?", likeCount: 6, isAuthor: false, createdAt: iso(3), liked: false },
  ],
  5103: [
    { id: 6031, postId: 5103, author: ANON("현직프론트"), content: "완성도 높은 2개를 추천합니다. 면접에서 깊게 물어볼 거리가 있어야 해요.", likeCount: 22, isAuthor: false, createdAt: iso(1), liked: true },
  ],
  5104: [],
  5105: [],
};

/* ── 인기글 ── */
const HOT_POSTS: { title: string; comments: number; views: number }[] = [
  { title: "네이버 프론트엔드 최종 합격 후기 — 6개월 준비 회고", comments: 2, views: 5120 },
  { title: "카카오 프론트엔드 1차 기술면접 후기 (React 상태관리 집중 질문)", comments: 3, views: 3284 },
  { title: "토스 코딩테스트 통과 전략 — 자료구조보다 구현력", comments: 0, views: 2190 },
];

/* ── AI 추천 태그 (resultJson 은 {tags,confidence,applied} 의 JSON 문자열) ── */
const AI_TAGS: Record<number, AiTagResult> = {
  5101: {
    postId: 5101,
    taskType: "COMMUNITY_TAG_SUGGEST",
    status: "SUCCESS",
    resultJson: JSON.stringify({
      tags: ["상태관리", "리렌더링 최적화", "1차면접"],
      confidence: 0.82,
      applied: false,
    }),
  },
  5102: {
    postId: 5102,
    taskType: "COMMUNITY_TAG_SUGGEST",
    status: "SUCCESS",
    resultJson: JSON.stringify({
      tags: ["합격수기", "배포경험", "성능개선"],
      confidence: 0.91,
      applied: true,
    }),
  },
};

/* ── 가이드라인 (published) — *Json 필드는 JSON 문자열 ── */
const GUIDELINE_OKS: string[] = [
  "회사·전형에 대한 부정적 평가 (경험에 기반한다면)",
  "면접 질문·과정 복기 (기출, 단계, 분위기, 체감 난이도)",
  "연봉·처우 등 민감한 주제의 경험 기반 토론",
  "내용을 향한 날 선 의견과 반박",
];
const GUIDELINE_NOS: string[] = [
  "부서·직급 조합 등으로 특정인을 알아볼 수 있게 쓰는 것",
  "인신공격·혐오 표현",
  "경험하지 않은 전형을 사실처럼 쓰는 지어낸 후기",
  "출시 전 제품 정보 등 회사의 미공개 기밀",
];
const GUIDELINE_RULES: GuidelineRule[] = [
  { t: "개인 특정·신상 노출", s: 0, b: "실명, 연락처, 또는 부서·직급·시기 조합으로 누구인지 알 수 있는 서술." },
  { t: "인신공격·혐오 표현", s: 0, b: "특정 이용자나 집단을 향한 모욕·위협, 출신·성별·연령 등에 대한 비하." },
  { t: "허위 사실·조작된 후기", s: 0, b: "경험하지 않은 전형의 후기, 의도적인 평판 조작." },
  { t: "광고·스팸·도배", s: 0, b: "영리 목적의 홍보, 동일 내용 반복 게시, 외부 유도 링크." },
  { t: "불법 정보·기밀 유출", s: 1, b: "법령 위반 콘텐츠, 기업의 미공개 기밀·내부 문서 유출." },
];
const GUIDELINE_PARAMS: GuidelineParams = { blind: 3, sla: 24, expire: 90, s1: 7, s2: 30, appeal: 30 };

const GUIDELINE: GuidelineData = {
  id: 1,
  versionLabel: "v1.0",
  lede:
    "CareerTuner 커뮤니티는 면접·취업 경험을 솔직하게 나누는 곳입니다. **솔직함이 핵심 가치**이기 때문에 글을 미리 검열하지 않습니다. 대신 다른 사람에게 실제 피해를 주는 행동만 좁고 명확하게 금지하고, 위반에는 단계적으로 대응합니다.",
  oksJson: JSON.stringify(GUIDELINE_OKS),
  nosJson: JSON.stringify(GUIDELINE_NOS),
  rulesJson: JSON.stringify(GUIDELINE_RULES),
  paramsJson: JSON.stringify(GUIDELINE_PARAMS),
  publishedAt: iso(6),
};

/* ── 라우트 ── */
export const communityRoutes: MockRoute[] = [
  // 게시글 목록: category(enum)/sort 필터는 데모에선 단순화하되 enum 일치 시 해당 카테고리만 반환
  {
    method: "GET",
    pattern: /^\/community\/posts$/,
    handler: (ctx: MockContext): PostPageData => {
      const { page, size } = pageOf(ctx, 20);
      const categoryEnum = ctx.query.get("category");
      const filtered = categoryEnum ? POSTS.filter((p) => p.category === categoryEnum) : POSTS;
      return { posts: filtered, total: filtered.length, page, size };
    },
  },
  // 인기글 (숫자가 아니라 'hot' 이므로 상세 패턴과 충돌하지 않음)
  {
    method: "GET",
    pattern: /^\/community\/posts\/hot$/,
    handler: ok(HOT_POSTS),
  },
  // 게시글 상세
  {
    method: "GET",
    pattern: /^\/community\/posts\/(\d+)$/,
    handler: (ctx: MockContext): BackendPost => {
      const id = Number(ctx.params[0]);
      return POST_BY_ID.get(id) ?? POSTS[0];
    },
  },
  // 게시글 댓글 목록
  {
    method: "GET",
    pattern: /^\/community\/posts\/(\d+)\/comments$/,
    handler: (ctx: MockContext): CommunityComment[] => {
      const id = Number(ctx.params[0]);
      return COMMENTS[id] ?? [];
    },
  },
  // AI 추천 태그
  {
    method: "GET",
    pattern: /^\/community\/posts\/(\d+)\/ai-tags$/,
    handler: (ctx: MockContext): AiTagResult => {
      const id = Number(ctx.params[0]);
      return (
        AI_TAGS[id] ?? { postId: id, taskType: "COMMUNITY_TAG_SUGGEST", status: "PENDING", resultJson: null }
      );
    },
  },
  // 가이드라인 (게시 버전)
  {
    method: "GET",
    pattern: /^\/community\/guidelines\/published$/,
    handler: ok(GUIDELINE),
  },
  // 게시글 작성 → 생성된 postId
  {
    method: "POST",
    pattern: /^\/community\/posts$/,
    handler: ok({ postId: 5199 }),
  },
  // 게시글 수정 (api<void>)
  {
    method: "PUT",
    pattern: /^\/community\/posts\/(\d+)$/,
    handler: ok(null),
  },
  // 게시글 삭제 (api<void>)
  {
    method: "DELETE",
    pattern: /^\/community\/posts\/(\d+)$/,
    handler: ok(null),
  },
  // 댓글 작성 → 생성된 댓글 echo
  {
    method: "POST",
    pattern: /^\/community\/posts\/(\d+)\/comments$/,
    handler: (ctx: MockContext): CommunityComment => {
      const postId = Number(ctx.params[0]);
      const content = (ctx.body as { content?: string } | null)?.content ?? "";
      return {
        id: 6900 + Math.floor(Math.random() * 99),
        postId,
        author: DEMO_AUTHOR,
        content,
        likeCount: 0,
        isAuthor: true,
        createdAt: iso(0),
        liked: false,
      };
    },
  },
  // 댓글 삭제 (api<void>)
  {
    method: "DELETE",
    pattern: /^\/community\/comments\/(\d+)$/,
    handler: ok(null),
  },
  // 좋아요/북마크 토글 → {active}. body.reactionType 가 활성/해제 토글이라 가정해 active:true 반환
  {
    method: "POST",
    pattern: /^\/community\/reactions$/,
    handler: ok({ active: true }),
  },
  // 신고 접수 (api<void>)
  {
    method: "POST",
    pattern: /^\/community\/reports$/,
    handler: ok(null),
  },
];
