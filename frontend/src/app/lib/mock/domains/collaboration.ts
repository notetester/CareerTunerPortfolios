import type { MockRoute } from "../registry";
import { demoApplicationCases, demoUser } from "../data";
// 개인 차단 정책 mock — 차단한 방 숨김·차단 사용자 메시지 톰스톤 처리에 사용 (additive).
import {
  mockConversationBlocked,
  mockPrivacyAllows,
  registerPrivacyMockConversations,
  registerPrivacyMockUsers,
} from "./privacy";
import type {
  AttachmentShareMode,
  CollaborationUser,
  ConversationSummaryResponse,
  ConversationType,
  FriendRequestResponse,
  FriendResponse,
  MessageAttachmentResponse,
  MessageKind,
  MessagePreviewResponse,
  MessageResponse,
  SharedPostingResponse,
} from "@/features/collaboration/types/collaboration";

const currentUser: CollaborationUser = {
  id: demoUser.id,
  name: demoUser.name,
  email: demoUser.email,
  plan: demoUser.plan,
  relationStatus: "SELF",
};

const users: CollaborationUser[] = [
  currentUser,
  { id: 9201, name: "박민지", email: "minji.park@example.com", plan: "PRO", relationStatus: "FRIEND" },
  { id: 9202, name: "이서준", email: "seojoon.lee@example.com", plan: "FREE", relationStatus: "FRIEND" },
  { id: 9203, name: "최하린", email: "harin.choi@example.com", plan: "FREE", relationStatus: "NONE" },
  { id: 9204, name: "정도윤", email: "doyoon.jung@example.com", plan: "PRO", relationStatus: "REQUESTED" },
  { id: 9205, name: "강유나", email: "yuna.kang@example.com", plan: "FREE", relationStatus: "PENDING_INCOMING" },
];

// 차단 mock 디렉터리에 사용자 등록(이름/이메일 표시·관계 평가용).
registerPrivacyMockUsers(
  users
    .filter((user) => user.id !== currentUser.id)
    .map((user) => ({
      id: user.id,
      name: user.name,
      email: user.email,
      relation: user.relationStatus === "FRIEND" ? ("friend" as const) : ("stranger" as const),
    })),
);

let friends: FriendResponse[] = [
  { user: users[1], friendsSince: new Date(Date.now() - 12 * 86_400_000).toISOString() },
  { user: users[2], friendsSince: new Date(Date.now() - 4 * 86_400_000).toISOString() },
];

let incomingRequests: FriendRequestResponse[] = [
  {
    id: 8101,
    requester: users[5],
    receiver: currentUser,
    status: "PENDING",
    createdAt: new Date(Date.now() - 2 * 86_400_000).toISOString(),
    respondedAt: null,
  },
];

let outgoingRequests: FriendRequestResponse[] = [
  {
    id: 8102,
    requester: currentUser,
    receiver: users[4],
    status: "PENDING",
    createdAt: new Date(Date.now() - 1 * 86_400_000).toISOString(),
    respondedAt: null,
  },
];

const posting = (applicationCaseId: number): SharedPostingResponse => {
  const applicationCase = demoApplicationCases.find((item) => item.id === applicationCaseId) ?? demoApplicationCases[0];
  return {
    applicationCaseId: applicationCase.id,
    companyName: applicationCase.companyName,
    jobTitle: applicationCase.jobTitle,
    deadlineDate: applicationCase.deadlineDate,
    sourceType: applicationCase.sourceType,
  };
};

const attachment = (
  fileId: number,
  originalName: string,
  shareMode: AttachmentShareMode,
  availability: MessageAttachmentResponse["availability"] = "AVAILABLE",
  // LOCAL 공유일 때만 의미 — 소유자 데스크톱이 온라인이면 다운로드 가능(서버 게이트 미러)
  ownerDesktopOnline?: boolean,
): MessageAttachmentResponse => {
  const resolvedAvailability = shareMode === "LOCAL" ? "LOCAL_ONLY" : availability;
  const downloadable = shareMode === "LOCAL"
    ? ownerDesktopOnline === true
    : resolvedAvailability === "AVAILABLE";
  return {
    fileId,
    originalName,
    contentType: "application/pdf",
    sizeBytes: 384_000,
    shareMode,
    availability: resolvedAvailability,
    expiresAt: shareMode === "TEMPORARY" ? new Date(Date.now() + 72 * 60 * 60 * 1000).toISOString() : null,
    downloadUrl: downloadable ? `/api/collaboration/files/${fileId}/content` : null,
    ownerDesktopOnline: shareMode === "LOCAL" ? ownerDesktopOnline === true : null,
  };
};

let messagesByConversation: Record<number, MessageResponse[]> = {
  7001: [
    {
      id: 9901,
      conversationId: 7001,
      sender: users[1],
      mine: false,
      kind: "CHAT",
      content: "네이버 프론트엔드 공고 준비하는 분들 자료 같이 모아봐요.",
      attachments: [],
      sharedPostings: [posting(102)],
      createdAt: new Date(Date.now() - 20 * 60 * 1000).toISOString(),
    },
    {
      id: 9902,
      conversationId: 7001,
      sender: currentUser,
      mine: true,
      kind: "NOTE",
      content: "제가 정리한 면접 질문 파일도 올려둘게요.",
      attachments: [attachment(7701, "frontend-interview-notes.pdf", "TEMPORARY")],
      sharedPostings: [],
      createdAt: new Date(Date.now() - 11 * 60 * 1000).toISOString(),
    },
  ],
  7002: [
    {
      id: 9910,
      conversationId: 7002,
      sender: users[2],
      mine: false,
      kind: "CHAT",
      content: "이력서 피드백 받을 사람 있나요?",
      attachments: [],
      sharedPostings: [],
      createdAt: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
    },
    {
      // LOCAL 공유 — 소유자 데스크톱 온라인: 웹에서도 바로 받을 수 있는 상태
      id: 9911,
      conversationId: 7002,
      sender: users[2],
      mine: false,
      kind: "CHAT",
      content: "포트폴리오 원본은 제 PC 공유 폴더에서 바로 받아가세요. 지금 데스크톱 켜져 있어요.",
      attachments: [attachment(7703, "portfolio-master.psd", "LOCAL", "LOCAL_ONLY", true)],
      sharedPostings: [],
      createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
    },
  ],
  7003: [
    {
      id: 9920,
      conversationId: 7003,
      sender: users[1],
      mine: false,
      kind: "CHAT",
      content: "스터디 자료는 유료 플랜 저장소에 올려둔 파일로 공유할게요.",
      attachments: [attachment(7702, "system-design-study.pdf", "CLOUD")],
      sharedPostings: [],
      createdAt: new Date(Date.now() - 90 * 60 * 1000).toISOString(),
    },
    {
      // LOCAL 공유 — 소유자 데스크톱 오프라인: 온라인이 될 때까지 다운로드 비활성
      id: 9921,
      conversationId: 7003,
      sender: users[1],
      mine: false,
      kind: "CHAT",
      content: "대용량 실습 데이터는 제 데스크톱에서 로컬 공유로 걸어뒀어요. 퇴근하면 꺼질 수 있어요.",
      attachments: [attachment(7704, "system-design-dataset.zip", "LOCAL", "LOCAL_ONLY", false)],
      sharedPostings: [],
      createdAt: new Date(Date.now() - 80 * 60 * 1000).toISOString(),
    },
  ],
};

let conversations: ConversationSummaryResponse[] = [
  {
    id: 7001,
    type: "PUBLIC",
    title: "프론트엔드 취준 자료방",
    description: "프론트엔드 공고, 면접 질문, 포트폴리오 자료 공유",
    displayName: "프론트엔드 취준 자료방",
    locked: false,
    memberCount: 18,
    joined: true,
    muted: false,
    peer: null,
    latestMessage: null,
    unreadCount: 1,
    updatedAt: new Date(Date.now() - 11 * 60 * 1000).toISOString(),
  },
  {
    id: 7002,
    type: "DIRECT",
    title: null,
    description: null,
    displayName: "이서준",
    locked: false,
    memberCount: 2,
    joined: true,
    muted: false,
    peer: users[2],
    latestMessage: null,
    unreadCount: 0,
    updatedAt: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
  },
  {
    id: 7003,
    type: "PRIVATE",
    title: "시스템 설계 스터디",
    description: "초대 또는 비밀번호로 참가하는 비공개 스터디",
    displayName: "시스템 설계 스터디",
    locked: true,
    memberCount: 6,
    joined: true,
    muted: true,
    peer: null,
    latestMessage: null,
    unreadCount: 0,
    updatedAt: new Date(Date.now() - 90 * 60 * 1000).toISOString(),
  },
  {
    id: 7004,
    type: "PUBLIC",
    title: "신입 백엔드 면접 공유",
    description: "Java, Spring, DB 면접 복기와 예상 질문",
    displayName: "신입 백엔드 면접 공유",
    locked: false,
    memberCount: 24,
    joined: false,
    muted: false,
    peer: null,
    latestMessage: null,
    unreadCount: 0,
    updatedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
  },
];

// 차단 mock 디렉터리에 채팅방 등록(차단 목록의 방 제목/유형 표시용).
registerPrivacyMockConversations(
  conversations.map((conversation) => ({ id: conversation.id, title: conversation.displayName, type: conversation.type })),
);

function latestMessage(conversationId: number): MessagePreviewResponse | null {
  const messages = messagesByConversation[conversationId] ?? [];
  const latest = messages[messages.length - 1];
  if (!latest) return null;
  return {
    id: latest.id,
    kind: latest.kind,
    content: latest.content,
    createdAt: latest.createdAt,
  };
}

function withLatest(conversation: ConversationSummaryResponse): ConversationSummaryResponse {
  return {
    ...conversation,
    latestMessage: latestMessage(conversation.id),
    updatedAt: latestMessage(conversation.id)?.createdAt ?? conversation.updatedAt,
  };
}

function visibleConversations(): ConversationSummaryResponse[] {
  return conversations
    .filter((conversation) => conversation.joined)
    // 차단한 채팅방은 목록에서 숨긴다(방 숨김 + 관련 초대 차단 — 개인 차단 정책).
    .filter((conversation) => !mockConversationBlocked(conversation.id))
    .map(withLatest)
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
}

function roomSearch(keyword: string): ConversationSummaryResponse[] {
  const lowered = keyword.trim().toLowerCase();
  return conversations
    .filter((conversation) => conversation.type === "PUBLIC" || conversation.type === "PRIVATE")
    .filter((conversation) => {
      if (!lowered) return true;
      return `${conversation.displayName} ${conversation.description ?? ""}`.toLowerCase().includes(lowered);
    })
    .map(withLatest);
}

function nextId(items: Array<{ id: number }>, base: number): number {
  return Math.max(base, ...items.map((item) => item.id)) + 1;
}

function requireConversation(id: number): ConversationSummaryResponse {
  const conversation = conversations.find((item) => item.id === id);
  if (!conversation) throw new Error("채팅방을 찾을 수 없습니다.");
  return conversation;
}

function normalizeMessageKind(kind: unknown): MessageKind {
  return kind === "NOTE" ? "NOTE" : "CHAT";
}

/** 차단한 사용자의 메시지는 뷰어 기준 톰스톤 처리(blocked=true, 본문·첨부 제거 — 서버측 처리 미러). */
function withViewerBlocked(message: MessageResponse): MessageResponse & { blocked?: boolean } {
  if (message.mine || mockPrivacyAllows(message.sender.id, "content.roomMessage")) return message;
  return { ...message, blocked: true, content: null, attachments: [], sharedPostings: [] };
}

function normalizeShareMode(mode: unknown): AttachmentShareMode {
  return mode === "CLOUD" || mode === "LOCAL" ? mode : "TEMPORARY";
}

export const collaborationRoutes: MockRoute[] = [
  {
    method: "GET",
    pattern: /^\/collaboration\/users\/search$/,
    handler: ({ query }) => {
      const keyword = (query.get("keyword") ?? "").trim().toLowerCase();
      const limit = Number(query.get("limit") ?? 20) || 20;
      if (!keyword) return [];
      return users
        .filter((user) => user.id !== currentUser.id)
        .filter((user) => `${user.name} ${user.email}`.toLowerCase().includes(keyword))
        .slice(0, limit);
    },
  },
  { method: "GET", pattern: /^\/collaboration\/friends$/, handler: () => friends },
  {
    method: "DELETE",
    pattern: /^\/collaboration\/friends\/(\d+)$/,
    handler: ({ params }) => {
      const userId = Number(params[0]);
      friends = friends.filter((friend) => friend.user.id !== userId);
      const user = users.find((item) => item.id === userId);
      if (user) user.relationStatus = "NONE";
      return null;
    },
  },
  { method: "GET", pattern: /^\/collaboration\/friend-requests\/incoming$/, handler: () => incomingRequests },
  { method: "GET", pattern: /^\/collaboration\/friend-requests\/outgoing$/, handler: () => outgoingRequests },
  {
    method: "POST",
    pattern: /^\/collaboration\/friend-requests$/,
    handler: ({ body }) => {
      const request = body as { targetUserId?: number };
      const target = users.find((user) => user.id === Number(request?.targetUserId));
      if (!target) throw new Error("사용자를 찾을 수 없습니다.");
      target.relationStatus = "REQUESTED";
      const created: FriendRequestResponse = {
        id: nextId([...incomingRequests, ...outgoingRequests], 8100),
        requester: currentUser,
        receiver: target,
        status: "PENDING",
        createdAt: new Date().toISOString(),
        respondedAt: null,
      };
      outgoingRequests = [created, ...outgoingRequests];
      return created;
    },
  },
  {
    method: "POST",
    pattern: /^\/collaboration\/friend-requests\/(\d+)\/accept$/,
    handler: ({ params }) => {
      const id = Number(params[0]);
      const target = incomingRequests.find((request) => request.id === id);
      if (!target) return null;
      incomingRequests = incomingRequests.filter((request) => request.id !== id);
      target.status = "ACCEPTED";
      target.respondedAt = new Date().toISOString();
      target.requester.relationStatus = "FRIEND";
      friends = [{ user: target.requester, friendsSince: target.respondedAt }, ...friends];
      return target;
    },
  },
  {
    method: "POST",
    pattern: /^\/collaboration\/friend-requests\/(\d+)\/decline$/,
    handler: ({ params }) => {
      const id = Number(params[0]);
      const target = incomingRequests.find((request) => request.id === id);
      if (!target) return null;
      incomingRequests = incomingRequests.filter((request) => request.id !== id);
      target.status = "DECLINED";
      target.respondedAt = new Date().toISOString();
      target.requester.relationStatus = "NONE";
      return target;
    },
  },
  { method: "GET", pattern: /^\/collaboration\/conversations$/, handler: () => visibleConversations() },
  {
    method: "GET",
    pattern: /^\/collaboration\/conversations\/discover$/,
    handler: ({ query }) => roomSearch(query.get("keyword") ?? ""),
  },
  {
    method: "POST",
    pattern: /^\/collaboration\/conversations\/direct$/,
    handler: ({ body }) => {
      const request = body as { targetUserId?: number };
      const peer = users.find((user) => user.id === Number(request?.targetUserId));
      if (!peer) throw new Error("사용자를 찾을 수 없습니다.");
      const existing = conversations.find((conversation) => conversation.type === "DIRECT" && conversation.peer?.id === peer.id);
      if (existing) {
        existing.joined = true;
        return withLatest(existing);
      }
      const created: ConversationSummaryResponse = {
        id: nextId(conversations, 7000),
        type: "DIRECT",
        title: null,
        description: null,
        displayName: peer.name,
        locked: false,
        memberCount: 2,
        joined: true,
        muted: false,
        peer,
        latestMessage: null,
        unreadCount: 0,
        updatedAt: new Date().toISOString(),
      };
      conversations = [created, ...conversations];
      messagesByConversation[created.id] = [];
      registerPrivacyMockConversations([{ id: created.id, title: created.displayName, type: created.type }]);
      return created;
    },
  },
  {
    method: "POST",
    pattern: /^\/collaboration\/conversations$/,
    handler: ({ body }) => {
      const request = body as {
        type?: ConversationType;
        title?: string;
        description?: string | null;
        password?: string | null;
        memberUserIds?: number[];
      };
      const type = request?.type === "PUBLIC" || request?.type === "PRIVATE" ? request.type : "GROUP";
      const created: ConversationSummaryResponse = {
        id: nextId(conversations, 7000),
        type,
        title: request?.title || "새 채팅방",
        description: request?.description || null,
        displayName: request?.title || "새 채팅방",
        locked: type === "PRIVATE",
        memberCount: 1 + (request?.memberUserIds?.length ?? 0),
        joined: true,
        muted: false,
        peer: null,
        latestMessage: null,
        unreadCount: 0,
        updatedAt: new Date().toISOString(),
      };
      conversations = [created, ...conversations];
      messagesByConversation[created.id] = [];
      registerPrivacyMockConversations([{ id: created.id, title: created.displayName, type: created.type }]);
      return created;
    },
  },
  {
    method: "POST",
    pattern: /^\/collaboration\/conversations\/(\d+)\/join$/,
    handler: ({ params }) => {
      const conversation = requireConversation(Number(params[0]));
      conversation.joined = true;
      return withLatest(conversation);
    },
  },
  {
    method: "POST",
    pattern: /^\/collaboration\/conversations\/(\d+)\/invites$/,
    handler: ({ params, body }) => {
      const conversation = requireConversation(Number(params[0]));
      const request = body as { userIds?: number[] };
      conversation.memberCount += request?.userIds?.length ?? 0;
      return withLatest(conversation);
    },
  },
  {
    method: "PATCH",
    pattern: /^\/collaboration\/conversations\/(\d+)\/mute$/,
    handler: ({ params, body }) => {
      const conversation = requireConversation(Number(params[0]));
      const request = body as { muted?: boolean };
      conversation.muted = request?.muted === true;
      return withLatest(conversation);
    },
  },
  {
    method: "GET",
    pattern: /^\/collaboration\/conversations\/(\d+)\/messages$/,
    handler: ({ params, query }) => {
      const limit = Number(query.get("limit") ?? 120) || 120;
      return (messagesByConversation[Number(params[0])] ?? []).slice(-limit).map(withViewerBlocked);
    },
  },
  {
    method: "POST",
    pattern: /^\/collaboration\/conversations\/(\d+)\/messages$/,
    handler: ({ params, body }) => {
      const conversationId = Number(params[0]);
      const conversation = requireConversation(conversationId);
      const request = body as {
        kind?: MessageKind;
        content?: string | null;
        attachmentFileIds?: number[];
        attachmentShareMode?: AttachmentShareMode | null;
        sharedApplicationCaseIds?: number[];
      };
      const files = request?.attachmentFileIds ?? [];
      const shareMode = normalizeShareMode(request?.attachmentShareMode);
      const existing = messagesByConversation[conversationId] ?? [];
      const created: MessageResponse = {
        id: nextId(Object.values(messagesByConversation).flat(), 9900),
        conversationId,
        sender: currentUser,
        mine: true,
        kind: normalizeMessageKind(request?.kind),
        content: request?.content ?? null,
        attachments: files.map((fileId) => attachment(fileId, `shared-file-${fileId}.pdf`, shareMode)),
        sharedPostings: (request?.sharedApplicationCaseIds ?? []).map(posting),
        createdAt: new Date().toISOString(),
      };
      messagesByConversation[conversationId] = [...existing, created];
      conversation.updatedAt = created.createdAt;
      return created;
    },
  },
];
