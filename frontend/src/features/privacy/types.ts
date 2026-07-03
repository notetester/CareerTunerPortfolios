// 개인 차단/허용 정책 타입 — 백엔드 privacy DTO·PrivacySurfaces 미러 (docs/PERSONAL_BLOCK_POLICY.md).
// 표면 키는 점(.) 표기 상속: 값이 없으면 마지막 조각을 떼며 상위 키로 올라가 첫 non-null 을 쓴다.

export type PolicyValue = "allow" | "block";

/* ── 관계 클래스 (백엔드 PrivacySurfaces.RELATIONS 순서 그대로) ── */
export type RelationKey = "stranger" | "friend" | "company" | "blockedAccount" | "blockedIp";

export const RELATIONS: RelationKey[] = ["stranger", "friend", "company", "blockedAccount", "blockedIp"];

export const RELATION_LABELS: Record<RelationKey, string> = {
  stranger: "모르는 사람",
  friend: "친구",
  company: "기업 계정",
  blockedAccount: "차단한 계정",
  blockedIp: "차단한 IP",
};

/* ── 베이스 표면 14종 (백엔드 PrivacySurfaces.BASE_SURFACES 순서 그대로) ── */
export const BASE_SURFACES = [
  "dm",
  "note",
  "friendRequest",
  "fileShare",
  "postingShare",
  "invite",
  "content.post",
  "content.comment",
  "content.reply",
  "content.roomMessage",
  "content.allAnonymousRoomMessages",
  "profile.viewMe",
  "profile.visibleToMe",
  "profile.searchMe",
] as const;

export type BaseSurface = (typeof BASE_SURFACES)[number];

export const SURFACE_LABELS: Record<string, string> = {
  dm: "1:1 채팅",
  note: "쪽지",
  friendRequest: "친구 요청",
  fileShare: "파일 공유",
  postingShare: "공고 공유",
  invite: "채팅방 초대",
  "content.post": "게시글 노출",
  "content.comment": "댓글 노출",
  "content.reply": "답글 노출",
  "content.roomMessage": "채팅방 메시지 노출",
  "content.allAnonymousRoomMessages": "방 전체 익명 메시지",
  "profile.viewMe": "내 프로필 조회",
  "profile.visibleToMe": "상대 프로필이 나에게 노출",
  "profile.searchMe": "검색 결과에 내 계정 노출",
};

/* ── 매트릭스 카테고리 (아코디언 4개) ── */
export interface SurfaceCategory {
  key: string;
  label: string;
  surfaces: string[];
}

export const SURFACE_CATEGORIES: SurfaceCategory[] = [
  { key: "direct", label: "다이렉트", surfaces: ["dm", "note", "friendRequest", "fileShare", "postingShare"] },
  { key: "invite", label: "초대", surfaces: ["invite"] },
  {
    key: "content",
    label: "콘텐츠 노출",
    surfaces: ["content.post", "content.comment", "content.reply", "content.roomMessage", "content.allAnonymousRoomMessages"],
  },
  { key: "profile", label: "프로필", surfaces: ["profile.viewMe", "profile.visibleToMe", "profile.searchMe"] },
];

/* ── 상세(하위) 표면 키 — 베이스 행의 "펼치기"로 노출 ── */
const ROOM_TYPES = [
  { key: "GROUP", label: "단체방" },
  { key: "PUBLIC", label: "공개방" },
  { key: "PRIVATE", label: "비공개방" },
] as const;

function inviteDetailRows(): Array<{ key: string; label: string }> {
  const rows: Array<{ key: string; label: string }> = [];
  for (const type of ROOM_TYPES) {
    rows.push({ key: `invite.${type.key}`, label: `${type.label} 초대` });
    for (const source of [
      { key: "creator", label: "개설자가 초대" },
      { key: "member", label: "참여자가 초대" },
    ]) {
      rows.push({ key: `invite.${type.key}.${source.key}`, label: `${type.label} · ${source.label}` });
      rows.push({ key: `invite.${type.key}.${source.key}.anonymous`, label: `${type.label} · ${source.label} · 익명` });
    }
  }
  return rows;
}

/** 베이스 표면 → 펼쳤을 때 보여줄 상세 표면 행 목록. 값이 없으면 상세 없음. */
export const SURFACE_DETAIL_ROWS: Record<string, Array<{ key: string; label: string }>> = {
  invite: inviteDetailRows(),
  "content.post": [{ key: "content.post.anonymous", label: "익명 게시글" }],
  "content.comment": [{ key: "content.comment.anonymous", label: "익명 댓글" }],
  "content.reply": [{ key: "content.reply.anonymous", label: "익명 답글" }],
  "content.roomMessage": [{ key: "content.roomMessage.anonymous", label: "익명 채팅방 메시지" }],
};

/* ── 채팅방 차단 파생 플래그 (conversation_block.flags_json) ── */
export const ROOM_BLOCK_FLAGS: Array<{ key: string; label: string; help: string }> = [
  { key: "inviteFromRoom", label: "이 방으로의 재초대 차단", help: "차단한 방으로 다시 초대하는 것을 막습니다. (기본 차단)" },
  { key: "memberCreatedRoomInvite", label: "구성원이 개설한 다른 방 초대 차단", help: "이 방 구성원이 새로 만든 방으로의 초대를 막습니다." },
  { key: "memberJoinedRoomInvite", label: "구성원이 속한 다른 방 초대 차단", help: "이 방 구성원이 참여 중인 다른 방으로의 초대를 막습니다." },
];

/* ── 상속 해석 — 백엔드 PrivacySurfaces.resolve 와 동일 로직 ── */

/** surface 키에서 시작해 상위로 올라가며 첫 allow/block 을 찾는다. 전부 없으면 null(호출자가 기본값 적용). */
export function resolveSurface(
  values: Record<string, string> | null | undefined,
  surface: string,
): PolicyValue | null {
  if (!values) return null;
  let key: string | null = surface;
  while (key) {
    const value = values[key];
    if (value === "allow" || value === "block") return value;
    const lastDot = key.lastIndexOf(".");
    key = lastDot < 0 ? null : key.slice(0, lastDot);
  }
  return null;
}

/** 관계별 시스템 기본값 — 차단 관계만 차단, 나머지는 허용. (백엔드 defaultValue 미러) */
export function defaultPolicyValue(relation: string): PolicyValue {
  return relation === "blockedAccount" || relation === "blockedIp" ? "block" : "allow";
}

/** 채팅방 차단 파생 플래그 기본값 — 그 방으로의 재초대만 기본 차단. (백엔드 roomFlagDefault 미러) */
export function roomFlagDefault(flag: string): PolicyValue {
  return flag.startsWith("inviteFromRoom") ? "block" : "allow";
}

/* ── REST DTO 미러 (backend/privacy/dto) ── */

/** GET /privacy/policy — overrides=저장된 값(상세 키 포함), effective=베이스 표면의 상속 해석 결과. */
export interface PrivacyPolicyResponse {
  relations: string[];
  baseSurfaces: string[];
  overrides: Record<string, Record<string, string>>;
  effective: Record<string, Record<string, string>>;
}

/** PUT /privacy/policy — relations[관계][표면키] = "allow" | "block" | ""(명시값 제거=상위 따름). */
export interface PrivacyPolicyUpdateRequest {
  relations: Record<string, Record<string, string>>;
}

/** 계정 차단 항목. flags 는 명시 설정만(비어 있으면 blockedAccount 정책=기본 차단을 따름). */
export interface UserBlockResponse {
  id: number;
  blockedUserId: number;
  blockedUserName: string | null;
  blockedUserEmail: string | null;
  flags: Record<string, string>;
  blockIp: boolean;
  memo: string | null;
  createdAt: string;
}

export interface UserBlockRequest {
  targetUserId: number;
  blockIp?: boolean;
  memo?: string;
}

/** flags[표면키] = "allow" | "block" | ""(명시값 제거). 예: 쪽지만 허용 → {"note":"allow"}. */
export interface UserBlockUpdateRequest {
  flags?: Record<string, string>;
  blockIp?: boolean;
  memo?: string;
}

/** IP 차단 항목 — 원본 IP 는 절대 노출되지 않는다(라벨·파생 출처·일치 계정 수만). */
export interface IpBlockResponse {
  id: number;
  label: string;
  sourceUserId: number | null;
  sourceUserName: string | null;
  matchedAccounts: number;
  createdAt: string;
}

export interface ConversationBlockRequest {
  conversationId: number;
  flags?: Record<string, string>;
}

/** 채팅방 차단 항목 — 파생 초대 차단 플래그 포함. */
export interface ConversationBlockResponse {
  id: number;
  conversationId: number;
  conversationTitle: string | null;
  conversationType: string | null;
  flags: Record<string, string>;
  createdAt: string;
}

/** 서버가 뷰어 기준으로 차단 톰스톤 처리한 응답 항목 표시(blocked=true → 톰스톤 렌더). */
export interface BlockedMarker {
  blocked?: boolean;
}
