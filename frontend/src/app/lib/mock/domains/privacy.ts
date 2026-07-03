// 데모/목: 개인 차단/허용 정책 (/privacy/**) — 정책·차단 CRUD 인메모리 (docs/PERSONAL_BLOCK_POLICY.md).
// 백엔드 PrivacyPolicyServiceImpl 의 의미론(점 표기 상속·빈 문자열=명시값 제거·파생 IP 차단)을 미러한다.
// 다른 도메인 mock(collaboration/f-area)이 차단 평가를 재사용할 수 있게 평가 헬퍼를 export 한다.
import type { MockRoute } from "../registry";
import {
  BASE_SURFACES,
  RELATIONS,
  defaultPolicyValue,
  resolveSurface,
  roomFlagDefault,
  type ConversationBlockResponse,
  type IpBlockResponse,
  type PrivacyPolicyResponse,
  type RelationKey,
  type UserBlockResponse,
} from "@/features/privacy/types";

/* ── 다른 mock 도메인이 등록하는 사용자/채팅방 디렉터리 (이름·관계 표시용) ── */

export interface PrivacyMockUser {
  id: number;
  name: string;
  email?: string;
  relation: "stranger" | "friend" | "company";
}

const knownUsers = new Map<number, PrivacyMockUser>();
const knownConversations = new Map<number, { id: number; title: string; type: string }>();

/* ── 콘텐츠 id → 작성자 디렉터리 (by-content 차단용) ──
 * 실서버에서는 community_post/community_comment 에서 작성자를 찾는다.
 * 목에서는 community.ts 의 시드 게시글/댓글과 동기화된 "서버만 아는 작성자 id"를 흉내낸다.
 * (익명 작성자의 id 는 클라이언트 어디에도 노출되지 않는 가상의 값) */

/** 데모 페르소나 "김데모" — community mock 의 DEMO_AUTHOR(id 9001)와 동일. */
const DEMO_VIEWER_ID = 9001;

interface MockContentAuthor {
  userId: number;
  anonymous: boolean;
  name?: string;
  email?: string;
}

const contentAuthors = new Map<string, MockContentAuthor>([
  // 게시글 (community.ts POSTS 와 동기화)
  ["POST:5101", { userId: 9301, anonymous: true }], // 취준생A
  ["POST:5102", { userId: 9302, anonymous: true }], // 합격수기
  ["POST:5103", { userId: DEMO_VIEWER_ID, anonymous: false, name: "김데모" }], // 본인 글 — INVALID_INPUT 시연
  ["POST:5104", { userId: 9304, anonymous: true }], // 코테장인
  ["POST:5105", { userId: 9305, anonymous: true }], // 긴장한지원자
  // 댓글 (community.ts COMMENTS 와 동기화)
  ["COMMENT:6011", { userId: 9306, anonymous: true }], // 준비생B
  ["COMMENT:6012", { userId: DEMO_VIEWER_ID, anonymous: false, name: "김데모" }],
  ["COMMENT:6013", { userId: 9301, anonymous: true }], // 취준생A (OP)
  ["COMMENT:6021", { userId: 9307, anonymous: true }], // 취준3년차
  ["COMMENT:6022", { userId: DEMO_VIEWER_ID, anonymous: false, name: "김데모" }],
  ["COMMENT:6031", { userId: 9308, anonymous: true }], // 현직프론트
]);

/** collaboration/f-area mock 이 모듈 초기화 시 호출해 사용자 정보를 등록한다(업서트). */
export function registerPrivacyMockUsers(users: PrivacyMockUser[]): void {
  for (const user of users) knownUsers.set(user.id, user);
}

/** collaboration mock 이 채팅방(생성분 포함)을 등록한다(업서트). */
export function registerPrivacyMockConversations(conversations: Array<{ id: number; title: string; type: string }>): void {
  for (const conversation of conversations) knownConversations.set(conversation.id, conversation);
}

/* ── 인메모리 상태 ── */

let policyOverrides: Record<string, Record<string, string>> = {};

let userBlocks: UserBlockResponse[] = [
  // 시연 시드: 검색 결과의 최하린(9203) — 대화 이력이 없어 다른 화면에 영향 없음.
  {
    id: 4001,
    blockedUserId: 9203,
    blockedUserName: "최하린",
    blockedUserEmail: "harin.choi@example.com",
    masked: false,
    flags: {},
    blockIp: true,
    memo: "스팸성 친구 요청 반복",
    createdAt: new Date(Date.now() - 6 * 86_400_000).toISOString(),
  },
];

let ipBlocks: IpBlockResponse[] = [
  {
    id: 4501,
    label: "차단 IP #4501",
    sourceUserId: 9203,
    sourceUserName: "최하린",
    matchedAccounts: 2,
    createdAt: new Date(Date.now() - 6 * 86_400_000).toISOString(),
  },
];

let conversationBlocks: ConversationBlockResponse[] = [];

function nextId(items: Array<{ id: number }>, base: number): number {
  return Math.max(base, ...items.map((item) => item.id)) + 1;
}

/** 백엔드 applyFlag 미러 — "allow"/"block" 은 설정, 빈 문자열/null 은 명시값 제거(상위 따름). */
function applyFlag(flags: Record<string, string>, key: string, value: unknown): void {
  if (value === "allow" || value === "block") flags[key] = value;
  else if (value == null || value === "") delete flags[key];
  else throw new Error("정책 값은 allow/block/빈값만 가능합니다.");
}

function policyResponse(): PrivacyPolicyResponse {
  const effective: Record<string, Record<string, string>> = {};
  for (const relation of RELATIONS) {
    const row: Record<string, string> = {};
    for (const surface of BASE_SURFACES) {
      row[surface] = resolveSurface(policyOverrides[relation], surface) ?? defaultPolicyValue(relation);
    }
    effective[relation] = row;
  }
  return {
    relations: [...RELATIONS],
    baseSurfaces: [...BASE_SURFACES],
    overrides: JSON.parse(JSON.stringify(policyOverrides)) as Record<string, Record<string, string>>,
    effective,
  };
}

/* ── 다른 mock 도메인용 평가 헬퍼 (백엔드 allows() 우선순위 미러) ── */

/** 데모 사용자(뷰어) 기준으로 actor 의 표면 접근 허용 여부. */
export function mockPrivacyAllows(actorId: number | null | undefined, surface: string): boolean {
  if (actorId == null) return true;
  const block = userBlocks.find((item) => item.blockedUserId === actorId);
  if (block) {
    const explicit = resolveSurface(block.flags, surface);
    if (explicit) return explicit === "allow";
  }
  const relation: RelationKey = block
    ? "blockedAccount"
    : ipBlocks.some((item) => item.sourceUserId === actorId)
      ? "blockedIp"
      : (knownUsers.get(actorId)?.relation ?? "stranger");
  const value = resolveSurface(policyOverrides[relation], surface) ?? defaultPolicyValue(relation);
  return value === "allow";
}

/** 채팅방 자체가 차단(숨김)됐는지. */
export function mockConversationBlocked(conversationId: number): boolean {
  return conversationBlocks.some((item) => item.conversationId === conversationId);
}

/* ── 라우트 ── */

export const privacyRoutes: MockRoute[] = [
  { method: "GET", pattern: /^\/privacy\/policy$/, handler: () => policyResponse() },
  {
    method: "PUT",
    pattern: /^\/privacy\/policy$/,
    handler: ({ body }) => {
      const request = body as { relations?: Record<string, Record<string, string>> };
      for (const [relation, values] of Object.entries(request?.relations ?? {})) {
        if (!(RELATIONS as string[]).includes(relation) || !values) continue;
        const row = policyOverrides[relation] ?? {};
        for (const [surface, value] of Object.entries(values)) applyFlag(row, surface, value);
        policyOverrides = { ...policyOverrides, [relation]: row };
      }
      return policyResponse();
    },
  },

  { method: "GET", pattern: /^\/privacy\/blocks\/users$/, handler: () => userBlocks },
  {
    method: "POST",
    pattern: /^\/privacy\/blocks\/users$/,
    handler: ({ body }) => {
      const request = body as { targetUserId?: number; blockIp?: boolean; memo?: string };
      const targetUserId = Number(request?.targetUserId);
      if (!targetUserId) throw new Error("차단할 사용자를 찾을 수 없습니다.");
      const existing = userBlocks.find((item) => item.blockedUserId === targetUserId);
      if (existing) return existing;
      const known = knownUsers.get(targetUserId);
      const created: UserBlockResponse = {
        id: nextId(userBlocks, 4000),
        blockedUserId: targetUserId,
        blockedUserName: known?.name ?? `사용자 #${targetUserId}`,
        blockedUserEmail: known?.email ?? null,
        masked: false,
        flags: {},
        blockIp: request?.blockIp === true,
        memo: request?.memo ?? null,
        createdAt: new Date().toISOString(),
      };
      userBlocks = [created, ...userBlocks];
      if (created.blockIp) deriveIpBlock(created);
      return created;
    },
  },
  // 콘텐츠 id 기반 차단 — 익명 글/댓글용. 백엔드 blockUserByContent 의 의미론(익명이면 masked_label) 미러.
  {
    method: "POST",
    pattern: /^\/privacy\/blocks\/users\/by-content$/,
    handler: ({ body }) => {
      const request = body as { contentType?: string; contentId?: number; blockIp?: boolean; memo?: string };
      const contentType = request?.contentType === "POST" || request?.contentType === "COMMENT" ? request.contentType : null;
      const contentId = Number(request?.contentId);
      if (!contentType || !contentId) throw new Error("차단할 콘텐츠를 찾을 수 없습니다.");
      const author = contentAuthors.get(`${contentType}:${contentId}`);
      if (!author) throw new Error("차단할 콘텐츠를 찾을 수 없습니다.");
      if (author.userId === DEMO_VIEWER_ID) throw new Error("본인이 작성한 콘텐츠의 작성자는 차단할 수 없습니다.");
      const existing = userBlocks.find((item) => item.blockedUserId === author.userId);
      if (existing) return existing;
      // 익명 콘텐츠면 차단 목록에 실명 대신 표시할 라벨을 저장한다(익명성 유지).
      const maskedLabel = author.anonymous
        ? `익명 작성자 (${contentType === "POST" ? "게시글" : "댓글"} #${contentId})`
        : null;
      const known = knownUsers.get(author.userId);
      const created: UserBlockResponse = {
        id: nextId(userBlocks, 4000),
        blockedUserId: author.userId,
        blockedUserName: maskedLabel ?? author.name ?? known?.name ?? `사용자 #${author.userId}`,
        blockedUserEmail: maskedLabel ? null : author.email ?? known?.email ?? null,
        masked: maskedLabel !== null,
        flags: {},
        blockIp: request?.blockIp === true,
        memo: request?.memo ?? null,
        createdAt: new Date().toISOString(),
      };
      userBlocks = [created, ...userBlocks];
      if (created.blockIp) deriveIpBlock(created);
      return created;
    },
  },
  {
    method: "PUT",
    pattern: /^\/privacy\/blocks\/users\/(\d+)$/,
    handler: ({ params, body }) => {
      const block = userBlocks.find((item) => item.id === Number(params[0]));
      if (!block) throw new Error("차단 항목을 찾을 수 없습니다.");
      const request = body as { flags?: Record<string, string>; blockIp?: boolean; memo?: string };
      const flags = { ...block.flags };
      for (const [key, value] of Object.entries(request?.flags ?? {})) applyFlag(flags, key, value);
      block.flags = flags;
      if (request?.memo !== undefined && request.memo !== null) block.memo = request.memo;
      if (request?.blockIp !== undefined && request.blockIp !== block.blockIp) {
        block.blockIp = request.blockIp;
        if (block.blockIp) deriveIpBlock(block);
        else ipBlocks = ipBlocks.filter((item) => item.sourceUserId !== block.blockedUserId);
      }
      return { ...block };
    },
  },
  {
    method: "DELETE",
    pattern: /^\/privacy\/blocks\/users\/(\d+)$/,
    handler: ({ params }) => {
      const block = userBlocks.find((item) => item.id === Number(params[0]));
      if (block) {
        // 백엔드와 동일: 파생 IP 차단도 함께 해제
        ipBlocks = ipBlocks.filter((item) => item.sourceUserId !== block.blockedUserId);
        userBlocks = userBlocks.filter((item) => item.id !== block.id);
      }
      return null;
    },
  },

  { method: "GET", pattern: /^\/privacy\/blocks\/ips$/, handler: () => ipBlocks },
  {
    method: "DELETE",
    pattern: /^\/privacy\/blocks\/ips\/(\d+)$/,
    handler: ({ params }) => {
      ipBlocks = ipBlocks.filter((item) => item.id !== Number(params[0]));
      return null;
    },
  },

  { method: "GET", pattern: /^\/privacy\/blocks\/conversations$/, handler: () => conversationBlocks },
  {
    method: "POST",
    pattern: /^\/privacy\/blocks\/conversations$/,
    handler: ({ body }) => {
      const request = body as { conversationId?: number; flags?: Record<string, string> };
      const conversationId = Number(request?.conversationId);
      if (!conversationId) throw new Error("차단할 대화방을 찾을 수 없습니다.");
      const existing = conversationBlocks.find((item) => item.conversationId === conversationId);
      if (existing) return existing;
      const known = knownConversations.get(conversationId);
      const flags: Record<string, string> = {};
      for (const [key, value] of Object.entries(request?.flags ?? {})) applyFlag(flags, key, value);
      const created: ConversationBlockResponse = {
        id: nextId(conversationBlocks, 4700),
        conversationId,
        conversationTitle: known?.title ?? `채팅방 #${conversationId}`,
        conversationType: known?.type ?? null,
        flags,
        createdAt: new Date().toISOString(),
      };
      conversationBlocks = [created, ...conversationBlocks];
      return created;
    },
  },
  {
    method: "PUT",
    pattern: /^\/privacy\/blocks\/conversations\/(\d+)$/,
    handler: ({ params, body }) => {
      const block = conversationBlocks.find((item) => item.id === Number(params[0]));
      if (!block) throw new Error("차단 항목을 찾을 수 없습니다.");
      const flags = { ...block.flags };
      for (const [key, value] of Object.entries((body as Record<string, string>) ?? {})) applyFlag(flags, key, value);
      block.flags = flags;
      return { ...block };
    },
  },
  {
    method: "DELETE",
    pattern: /^\/privacy\/blocks\/conversations\/(\d+)$/,
    handler: ({ params }) => {
      conversationBlocks = conversationBlocks.filter((item) => item.id !== Number(params[0]));
      return null;
    },
  },
];

function deriveIpBlock(block: UserBlockResponse): void {
  if (ipBlocks.some((item) => item.sourceUserId === block.blockedUserId)) return;
  const id = nextId(ipBlocks, 4500);
  ipBlocks = [
    {
      id,
      label: `차단 IP #${id}`,
      sourceUserId: block.blockedUserId,
      sourceUserName: block.blockedUserName,
      matchedAccounts: 1 + (block.blockedUserId % 3),
      createdAt: new Date().toISOString(),
    },
    ...ipBlocks,
  ];
}

// 데모 편의: roomFlagDefault 를 이 모듈 사용처에서 재노출(다른 도메인 mock 이 초대 차단 평가에 사용 가능).
export { roomFlagDefault as mockRoomFlagDefault };
