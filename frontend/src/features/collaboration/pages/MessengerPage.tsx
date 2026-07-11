import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import type { ChangeEvent, ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import {
  Ban,
  Bell,
  BellOff,
  Briefcase,
  Check,
  Download,
  FileText,
  Globe2,
  Inbox,
  Lock,
  MessageSquare,
  Paperclip,
  Plus,
  RefreshCw,
  Search,
  Send,
  Settings2,
  UserPlus,
  UserX,
  Users,
  X,
} from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import { Badge } from "@/app/components/ui/badge";
import { cn } from "@/app/components/ui/utils";
import { listApplicationCases } from "@/features/applications/api/applicationCasesApi";
import type { ApplicationCase } from "@/features/applications/types/applicationCase";
import {
  SHARE_MODE_LABEL,
  acceptFriendRequest,
  createConversation,
  declineFriendRequest,
  discoverConversations,
  downloadCollaborationAttachment,
  inviteConversationMembers,
  joinConversation,
  listConversations,
  listFriends,
  listIncomingFriendRequests,
  listMessages,
  listOutgoingFriendRequests,
  muteConversation,
  openDirectConversation,
  searchUsers,
  sendFriendRequest,
  sendMessage,
  uploadCollaborationFile,
} from "../api/collaborationApi";
import type {
  AttachmentAvailability,
  AttachmentShareMode,
  CollaborationUser,
  ConversationSummaryResponse,
  ConversationType,
  FileAssetResponse,
  FriendRequestResponse,
  FriendResponse,
  MessageAttachmentResponse,
  MessageKind,
  MessageResponse,
} from "../types/collaboration";
import { RoomSettingsSheet } from "../components/RoomSettingsSheet";
// 개인 차단 진입점 — 코어(/api/privacy)는 사용만 하고 수정하지 않는다 (docs/PERSONAL_BLOCK_POLICY.md §4).
import { blockConversation, blockUser } from "@/features/privacy/api/privacyApi";
import { showBlockManageToast } from "@/features/privacy/components/blockToast";
import type { BlockedMarker } from "@/features/privacy/types";

const ROOM_TYPES: Array<{ value: Exclude<ConversationType, "DIRECT">; label: string; icon: LucideIcon }> = [
  { value: "GROUP", label: "친구 단체방", icon: Users },
  { value: "PUBLIC", label: "공개방", icon: Globe2 },
  { value: "PRIVATE", label: "비공개방", icon: Lock },
];

/** 웹/모바일에서 선택 가능한 공유 방식. LOCAL(PC 공유 폴더)은 데스크톱 앱 전용이라 옵션을 렌더하지 않는다. */
type WebShareMode = Exclude<AttachmentShareMode, "LOCAL">;

const SHARE_MODES: Array<{ value: WebShareMode; description: string }> = [
  { value: "TEMPORARY", description: "서버에 임시 보관 후 만료" },
  { value: "CLOUD", description: "유료 플랜 저장소 파일 공유" },
];

const MUTE_HELP = "알림 해제 방은 내 이름·키워드 언급 시에만 알림";

const AVAILABILITY_LABEL: Record<AttachmentAvailability, string> = {
  AVAILABLE: "다운로드 가능",
  EXPIRED: "만료됨",
  PLAN_INACTIVE: "플랜 중단",
  LOCAL_ONLY: "로컬 전용",
};

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "요청을 처리하지 못했습니다.";
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  let value = bytes;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function conversationTypeLabel(type: ConversationType): string {
  switch (type) {
    case "DIRECT":
      return "1:1";
    case "GROUP":
      return "단체";
    case "PUBLIC":
      return "공개";
    case "PRIVATE":
      return "비공개";
    default:
      return type;
  }
}

function userLabel(user: CollaborationUser | null | undefined): string {
  return user?.name || user?.email || "사용자";
}

function Panel({
  title,
  icon: Icon,
  action,
  children,
  id,
}: {
  title: string;
  icon: LucideIcon;
  action?: ReactNode;
  children: ReactNode;
  /** 헤더 하위메뉴(?tab=friends|discover) 스크롤 앵커용 */
  id?: string;
}) {
  return (
    <section id={id} className="rounded-lg border border-border bg-card scroll-mt-24">
      <div className="flex min-h-12 items-center justify-between gap-3 border-b border-border px-4 py-3">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <Icon className="size-4 text-primary" />
          {title}
        </h2>
        {action}
      </div>
      {children}
    </section>
  );
}

function UserLine({
  user,
  meta,
  action,
}: {
  user: CollaborationUser;
  meta?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex min-w-0 items-center gap-3 rounded-md border border-border bg-background px-3 py-2">
      <div className="flex size-9 shrink-0 items-center justify-center rounded-md bg-primary/10 text-sm font-bold text-primary">
        {userLabel(user).charAt(0)}
      </div>
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-semibold text-foreground">{userLabel(user)}</div>
        <div className="truncate text-xs text-muted-foreground">{user.email}</div>
        {meta}
      </div>
      {action}
    </div>
  );
}

function AttachmentButton({
  file,
  onDownload,
}: {
  file: MessageAttachmentResponse;
  onDownload: (file: MessageAttachmentResponse) => void;
}) {
  // LOCAL 공유 첨부는 소유자 데스크톱이 온라인일 때만 서버가 실제 전송을 허용한다.
  // (업로드 옵션은 데스크톱 전용으로 유지 — 웹/모바일은 다운로드만.)
  const isLocal = file.shareMode === "LOCAL";
  const ownerOnline = file.ownerDesktopOnline === true;
  const available = isLocal ? ownerOnline : file.availability === "AVAILABLE";
  const localHint = ownerOnline
    ? "소유자 데스크톱이 온라인이에요 — 지금 받을 수 있어요"
    : "소유자 데스크톱이 온라인일 때 받을 수 있어요";

  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-background/80 px-3 py-2">
      <div className="min-w-0">
        <div className="flex min-w-0 items-center gap-2 text-sm font-medium">
          <FileText className="size-4 shrink-0 text-muted-foreground" />
          <span className="truncate">{file.originalName}</span>
        </div>
        <div className="mt-0.5 text-xs text-muted-foreground">
          {isLocal
            ? `${SHARE_MODE_LABEL.LOCAL} · ${ownerOnline ? "소유자 온라인" : "소유자 오프라인"}`
            : SHARE_MODE_LABEL[file.shareMode]} · {formatBytes(file.sizeBytes)}
          {file.expiresAt ? ` · ${formatDateTime(file.expiresAt)} 만료` : ""}
        </div>
      </div>
      <Button
        type="button"
        size="sm"
        variant="outline"
        disabled={!available}
        onClick={() => onDownload(file)}
        title={isLocal ? localHint : AVAILABILITY_LABEL[file.availability]}
      >
        <Download className="size-4" />
        {available ? "받기" : isLocal ? "소유자 오프라인" : AVAILABILITY_LABEL[file.availability]}
      </Button>
    </div>
  );
}

export function MessengerPage() {
  // 헤더 하위메뉴 의도(?tab=friends|discover) 배선 — 단일 뷰라 해당 패널로 스크롤해 초점을 준다.
  const [searchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab");
  useEffect(() => {
    if (requestedTab !== "friends" && requestedTab !== "discover") return;
    const anchor = document.getElementById(requestedTab === "friends" ? "messenger-friends" : "messenger-discover");
    // 데이터 로드로 레이아웃이 잡힌 뒤 스크롤되도록 다음 프레임에 실행.
    const timer = window.setTimeout(() => anchor?.scrollIntoView({ behavior: "smooth", block: "start" }), 150);
    return () => window.clearTimeout(timer);
  }, [requestedTab]);
  const [conversations, setConversations] = useState<ConversationSummaryResponse[]>([]);
  const [discoverRooms, setDiscoverRooms] = useState<ConversationSummaryResponse[]>([]);
  const [friends, setFriends] = useState<FriendResponse[]>([]);
  const [incomingRequests, setIncomingRequests] = useState<FriendRequestResponse[]>([]);
  const [outgoingRequests, setOutgoingRequests] = useState<FriendRequestResponse[]>([]);
  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [applicationCases, setApplicationCases] = useState<ApplicationCase[]>([]);
  const [userResults, setUserResults] = useState<CollaborationUser[]>([]);

  const [activeConversationId, setActiveConversationId] = useState<number | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [roomSearch, setRoomSearch] = useState("");
  const [userKeyword, setUserKeyword] = useState("");
  const [joinPasswords, setJoinPasswords] = useState<Record<number, string>>({});
  const [createType, setCreateType] = useState<Exclude<ConversationType, "DIRECT">>("GROUP");
  const [createTitle, setCreateTitle] = useState("");
  const [createDescription, setCreateDescription] = useState("");
  const [createPassword, setCreatePassword] = useState("");
  const [createInviteIds, setCreateInviteIds] = useState<number[]>([]);

  const [messageKind, setMessageKind] = useState<MessageKind>("CHAT");
  const [messageText, setMessageText] = useState("");
  const [shareMode, setShareMode] = useState<WebShareMode>("TEMPORARY");
  const [temporaryHours, setTemporaryHours] = useState(72);
  const [pendingFiles, setPendingFiles] = useState<FileAssetResponse[]>([]);
  const [selectedApplicationIds, setSelectedApplicationIds] = useState<number[]>([]);

  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const activeConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === activeConversationId) ?? null,
    [activeConversationId, conversations],
  );

  const upsertConversation = useCallback((conversation: ConversationSummaryResponse) => {
    setConversations((current) => [
      conversation,
      ...current.filter((item) => item.id !== conversation.id),
    ]);
    setActiveConversationId(conversation.id);
  }, []);

  const refreshConversations = useCallback(async () => {
    const rows = await listConversations();
    setConversations(rows);
    setActiveConversationId((current) => {
      if (current && rows.some((item) => item.id === current)) return current;
      return rows[0]?.id ?? null;
    });
  }, []);

  const refreshFriendData = useCallback(async () => {
    const [friendRows, incoming, outgoing] = await Promise.all([
      listFriends(),
      listIncomingFriendRequests(),
      listOutgoingFriendRequests(),
    ]);
    setFriends(friendRows);
    setIncomingRequests(incoming);
    setOutgoingRequests(outgoing);
  }, []);

  const refreshDiscoverRooms = useCallback(async () => {
    setDiscoverRooms(await discoverConversations(roomSearch.trim(), 30));
  }, [roomSearch]);

  const loadMessages = useCallback(async (conversationId: number | null) => {
    if (!conversationId) {
      setMessages([]);
      return;
    }
    setMessages(await listMessages(conversationId, 120));
  }, []);

  const refreshAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [conversationRows, friendRows, incoming, outgoing, cases, rooms] = await Promise.all([
        listConversations(),
        listFriends(),
        listIncomingFriendRequests(),
        listOutgoingFriendRequests(),
        listApplicationCases({ view: "ACTIVE" }),
        discoverConversations("", 30),
      ]);
      setConversations(conversationRows);
      setFriends(friendRows);
      setIncomingRequests(incoming);
      setOutgoingRequests(outgoing);
      setApplicationCases(cases);
      setDiscoverRooms(rooms);
      setActiveConversationId((current) => {
        if (current && conversationRows.some((item) => item.id === current)) return current;
        return conversationRows[0]?.id ?? null;
      });
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshAll();
  }, [refreshAll]);

  useEffect(() => {
    void loadMessages(activeConversationId);
  }, [activeConversationId, loadMessages]);

  async function execute(action: () => Promise<void>, success?: string) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await action();
      if (success) setNotice(success);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  const searchUserResults = () => execute(async () => {
    setUserResults(userKeyword.trim() ? await searchUsers(userKeyword.trim(), 20) : []);
  });

  const createRoom = () => execute(async () => {
    if (!createTitle.trim()) {
      throw new Error("채팅방 이름을 입력해 주세요.");
    }
    const conversation = await createConversation({
      type: createType,
      title: createTitle.trim(),
      description: createDescription.trim() || null,
      password: createType === "PRIVATE" ? createPassword.trim() || null : null,
      memberUserIds: createInviteIds,
    });
    upsertConversation(conversation);
    setCreateTitle("");
    setCreateDescription("");
    setCreatePassword("");
    setCreateInviteIds([]);
    await refreshDiscoverRooms();
  }, "채팅방을 만들었습니다.");

  const requestFriend = (userId: number) => execute(async () => {
    await sendFriendRequest(userId);
    await refreshFriendData();
    if (userKeyword.trim()) setUserResults(await searchUsers(userKeyword.trim(), 20));
  }, "친구 요청을 보냈습니다.");

  const acceptRequest = (requestId: number) => execute(async () => {
    await acceptFriendRequest(requestId);
    await refreshFriendData();
  }, "친구 요청을 수락했습니다.");

  const declineRequest = (requestId: number) => execute(async () => {
    await declineFriendRequest(requestId);
    await refreshFriendData();
  }, "친구 요청을 거절했습니다.");

  const openDirect = (userId: number) => execute(async () => {
    upsertConversation(await openDirectConversation(userId));
    await refreshConversations();
  });

  const joinRoom = (conversation: ConversationSummaryResponse) => execute(async () => {
    upsertConversation(await joinConversation(conversation.id, joinPasswords[conversation.id]));
    await refreshDiscoverRooms();
  }, "채팅방에 참가했습니다.");

  const inviteFriend = (userId: number) => execute(async () => {
    if (!activeConversation || activeConversation.type === "DIRECT") return;
    upsertConversation(await inviteConversationMembers(activeConversation.id, [userId]));
  }, "친구를 초대했습니다.");

  const toggleMute = (conversation: ConversationSummaryResponse) => execute(async () => {
    const updated = await muteConversation(conversation.id, !conversation.muted);
    setConversations((current) => current.map((item) => (
      item.id === updated.id ? { ...item, muted: updated.muted } : item
    )));
  }, conversation.muted ? "채팅방 알림을 다시 켰습니다." : `채팅방 알림을 해제했습니다. ${MUTE_HELP}.`);

  const handleFiles = (event: ChangeEvent<HTMLInputElement>) => {
    const selected = Array.from(event.currentTarget.files ?? []);
    event.currentTarget.value = "";
    if (selected.length === 0) return;

    void execute(async () => {
      const uploaded = await Promise.all(selected.map((file) => uploadCollaborationFile(file)));
      setPendingFiles((current) => [...current, ...uploaded]);
    }, `${selected.length}개 파일을 첨부했습니다.`);
  };

  const sendCurrentMessage = () => execute(async () => {
    if (!activeConversation) throw new Error("먼저 채팅방을 선택해 주세요.");
    const content = messageText.trim();
    if (!content && pendingFiles.length === 0 && selectedApplicationIds.length === 0) {
      throw new Error("메시지, 파일, 공유할 공고 중 하나는 필요합니다.");
    }
    const sent = await sendMessage(activeConversation.id, {
      kind: messageKind,
      content: content || null,
      attachmentFileIds: pendingFiles.map((file) => file.id),
      attachmentShareMode: pendingFiles.length > 0 ? shareMode : null,
      temporaryHours: shareMode === "TEMPORARY" ? temporaryHours : null,
      sharedApplicationCaseIds: selectedApplicationIds,
    });
    setMessages((current) => [...current, sent]);
    setMessageText("");
    setPendingFiles([]);
    setSelectedApplicationIds([]);
    await refreshConversations();
  });

  const toggleCreateInvite = (userId: number) => {
    setCreateInviteIds((current) => (
      current.includes(userId) ? current.filter((id) => id !== userId) : [...current, userId]
    ));
  };

  const toggleApplicationShare = (applicationCaseId: number) => {
    setSelectedApplicationIds((current) => (
      current.includes(applicationCaseId)
        ? current.filter((id) => id !== applicationCaseId)
        : [...current, applicationCaseId]
    ));
  };

  const downloadAttachment = (file: MessageAttachmentResponse) => execute(async () => {
    await downloadCollaborationAttachment(file);
  });

  /** 사용자 차단 — 조용한 차단(상대에게 알리지 않음). 차단 직후 메시지 목록을 다시 받아 톰스톤을 반영한다. */
  const blockUserQuietly = (target: CollaborationUser) => execute(async () => {
    await blockUser({ targetUserId: target.id });
    showBlockManageToast(`${userLabel(target)}님을 차단했습니다.`);
    await Promise.all([refreshConversations(), loadMessages(activeConversationId)]);
  });

  /** 채팅방 차단 — 방 숨김 + 그 방 관련 초대 차단(연속 초대 테러 방지 파생 규칙 포함). */
  const blockRoomQuietly = (conversation: ConversationSummaryResponse) => execute(async () => {
    await blockConversation({ conversationId: conversation.id });
    showBlockManageToast(
      `'${conversation.displayName}' 채팅방을 차단했습니다.`,
      "방이 목록에서 숨겨지고 이 방 관련 초대가 차단됩니다.",
    );
    await refreshConversations();
  });

  return (
    <main className="min-h-[calc(100vh-8rem)] bg-muted/30 pb-28">
      <div className="mx-auto w-full max-w-[1500px] px-3 py-4 sm:px-6 lg:px-8">
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-bold tracking-tight text-foreground">
              <MessageSquare className="size-6 text-primary" />
              메신저
            </h1>
            <p className="mt-1 text-sm text-muted-foreground">
              친구, 단체방, 공개방, 비공개방에서 파일과 지원 공고를 함께 공유합니다.
            </p>
          </div>
          <Button type="button" variant="outline" onClick={() => void refreshAll()} disabled={loading || busy}>
            <RefreshCw className={cn("size-4", loading && "animate-spin")} />
            새로고침
          </Button>
        </div>

        {error && (
          <div className="mb-3 rounded-md border border-destructive/25 bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {error}
          </div>
        )}
        {notice && (
          <div className="mb-3 rounded-md border border-primary/20 bg-primary/10 px-4 py-3 text-sm text-primary">
            {notice}
          </div>
        )}

        <div className="grid gap-4 xl:grid-cols-[320px_minmax(320px,380px)_minmax(0,1fr)]">
          <div className="space-y-4">
            <Panel title="내 채팅방" icon={Inbox}>
              <div className="max-h-[520px] space-y-2 overflow-y-auto p-3">
                {loading ? (
                  <div className="rounded-md border border-border bg-background p-4 text-sm text-muted-foreground">채팅방을 불러오는 중입니다.</div>
                ) : conversations.length === 0 ? (
                  <div className="rounded-md border border-border bg-background p-4 text-sm text-muted-foreground">아직 참여 중인 채팅방이 없습니다.</div>
                ) : conversations.map((conversation) => (
                  <button
                    key={conversation.id}
                    type="button"
                    onClick={() => setActiveConversationId(conversation.id)}
                    className={cn(
                      "flex w-full min-w-0 flex-col gap-2 rounded-md border px-3 py-3 text-left transition-colors",
                      activeConversationId === conversation.id
                        ? "border-primary bg-primary/10"
                        : "border-border bg-background hover:bg-accent",
                    )}
                  >
                    <div className="flex min-w-0 items-center justify-between gap-2">
                      <div className="flex min-w-0 items-center gap-1.5">
                        <div className="min-w-0 truncate text-sm font-semibold text-foreground">{conversation.displayName}</div>
                        {conversation.muted && (
                          <BellOff className="size-3.5 shrink-0 text-muted-foreground" aria-label="알림 해제됨" />
                        )}
                      </div>
                      <Badge variant="outline">{conversationTypeLabel(conversation.type)}</Badge>
                    </div>
                    <div className="line-clamp-2 text-xs text-muted-foreground">
                      {conversation.latestMessage?.content || conversation.description || "새 메시지를 시작해 보세요."}
                    </div>
                    <div className="flex items-center justify-between text-xs text-muted-foreground">
                      <span>{conversation.memberCount}명 · {formatDateTime(conversation.updatedAt)}</span>
                      {conversation.unreadCount > 0 && <Badge>{conversation.unreadCount}</Badge>}
                    </div>
                  </button>
                ))}
              </div>
            </Panel>

            <Panel title="채팅방 만들기" icon={Plus}>
              <div className="space-y-3 p-3">
                <div className="grid grid-cols-3 gap-2">
                  {ROOM_TYPES.map((item) => (
                    <button
                      key={item.value}
                      type="button"
                      onClick={() => setCreateType(item.value)}
                      className={cn(
                        "flex min-h-16 flex-col items-center justify-center gap-1 rounded-md border px-2 text-xs font-semibold",
                        createType === item.value ? "border-primary bg-primary/10 text-primary" : "border-border text-muted-foreground hover:bg-accent",
                      )}
                    >
                      <item.icon className="size-4" />
                      {item.label}
                    </button>
                  ))}
                </div>
                <Input value={createTitle} onChange={(event) => setCreateTitle(event.target.value)} placeholder="채팅방 이름" />
                <Input value={createDescription} onChange={(event) => setCreateDescription(event.target.value)} placeholder="설명(선택)" />
                {createType === "PRIVATE" && (
                  <Input
                    type="password"
                    value={createPassword}
                    onChange={(event) => setCreatePassword(event.target.value)}
                    placeholder="비밀번호(초대만 허용하려면 비워두기)"
                  />
                )}
                {friends.length > 0 && (
                  <div className="space-y-2">
                    <div className="text-xs font-semibold text-muted-foreground">처음 초대할 친구</div>
                    <div className="flex flex-wrap gap-2">
                      {friends.slice(0, 8).map((friend) => (
                        <button
                          key={friend.user.id}
                          type="button"
                          onClick={() => toggleCreateInvite(friend.user.id)}
                          className={cn(
                            "rounded-md border px-2.5 py-1.5 text-xs font-medium",
                            createInviteIds.includes(friend.user.id)
                              ? "border-primary bg-primary/10 text-primary"
                              : "border-border text-muted-foreground hover:bg-accent",
                          )}
                        >
                          {friend.user.name}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
                <Button type="button" className="w-full" onClick={createRoom} disabled={busy}>
                  <Plus className="size-4" />
                  만들기
                </Button>
              </div>
            </Panel>
          </div>

          <div className="space-y-4">
            <Panel id="messenger-friends" title="친구" icon={Users}>
              <div className="space-y-3 p-3">
                <div className="flex gap-2">
                  <Input
                    value={userKeyword}
                    onChange={(event) => setUserKeyword(event.target.value)}
                    placeholder="이름 또는 이메일 검색"
                    onKeyDown={(event) => {
                      if (event.key === "Enter") searchUserResults();
                    }}
                  />
                  <Button type="button" size="icon" variant="outline" onClick={searchUserResults} disabled={busy} aria-label="사용자 검색">
                    <Search className="size-4" />
                  </Button>
                </div>

                {userResults.length > 0 && (
                  <div className="space-y-2">
                    {userResults.map((user) => (
                      <UserLine
                        key={user.id}
                        user={user}
                        meta={<div className="mt-1 text-xs text-muted-foreground">{user.relationStatus ?? "NONE"}</div>}
                        action={
                          <div className="flex items-center gap-1">
                            {user.relationStatus === "FRIEND" ? (
                              <Button type="button" size="sm" variant="outline" onClick={() => openDirect(user.id)} disabled={busy}>
                                <MessageSquare className="size-4" />
                                DM
                              </Button>
                            ) : user.relationStatus === "REQUESTED" ? (
                              <Button type="button" size="sm" variant="outline" disabled>
                                요청됨
                              </Button>
                            ) : user.relationStatus === "PENDING_INCOMING" ? (
                              <Button type="button" size="sm" variant="outline" disabled>
                                받은 요청
                              </Button>
                            ) : (
                              <Button type="button" size="sm" onClick={() => requestFriend(user.id)} disabled={busy}>
                                <UserPlus className="size-4" />
                                요청
                              </Button>
                            )}
                            {user.relationStatus !== "SELF" && (
                              <Button
                                type="button"
                                size="icon"
                                variant="outline"
                                className="text-destructive"
                                onClick={() => blockUserQuietly(user)}
                                disabled={busy}
                                aria-label="이 사용자 차단"
                                title="이 사용자 차단"
                              >
                                <UserX className="size-4" />
                              </Button>
                            )}
                          </div>
                        }
                      />
                    ))}
                  </div>
                )}

                {incomingRequests.length > 0 && (
                  <div className="space-y-2">
                    <div className="flex items-center gap-2 text-xs font-semibold text-muted-foreground">
                      <Bell className="size-3.5" />
                      받은 요청
                    </div>
                    {incomingRequests.map((request) => (
                      <UserLine
                        key={request.id}
                        user={request.requester}
                        action={
                          <div className="flex gap-1">
                            <Button type="button" size="icon" variant="outline" onClick={() => acceptRequest(request.id)} disabled={busy} aria-label="친구 요청 수락">
                              <Check className="size-4" />
                            </Button>
                            <Button type="button" size="icon" variant="outline" onClick={() => declineRequest(request.id)} disabled={busy} aria-label="친구 요청 거절">
                              <X className="size-4" />
                            </Button>
                          </div>
                        }
                      />
                    ))}
                  </div>
                )}

                <div className="space-y-2">
                  <div className="flex items-center justify-between text-xs font-semibold text-muted-foreground">
                    <span>친구 목록</span>
                    {outgoingRequests.length > 0 && <span>보낸 요청 {outgoingRequests.length}</span>}
                  </div>
                  {friends.length === 0 ? (
                    <div className="rounded-md border border-border bg-background p-4 text-sm text-muted-foreground">친구를 검색해서 연결해 보세요.</div>
                  ) : friends.map((friend) => (
                    <UserLine
                      key={friend.user.id}
                      user={friend.user}
                      meta={<div className="mt-1 text-xs text-muted-foreground">{formatDateTime(friend.friendsSince)} 친구</div>}
                      action={
                        <div className="flex gap-1">
                          <Button type="button" size="icon" variant="outline" onClick={() => openDirect(friend.user.id)} disabled={busy} aria-label="1대1 채팅 열기">
                            <MessageSquare className="size-4" />
                          </Button>
                          {activeConversation && activeConversation.type !== "DIRECT" && (
                            <Button type="button" size="icon" variant="outline" onClick={() => inviteFriend(friend.user.id)} disabled={busy} aria-label="현재 채팅방 초대">
                              <UserPlus className="size-4" />
                            </Button>
                          )}
                          <Button
                            type="button"
                            size="icon"
                            variant="outline"
                            className="text-destructive"
                            onClick={() => blockUserQuietly(friend.user)}
                            disabled={busy}
                            aria-label="이 사용자 차단"
                            title="이 사용자 차단"
                          >
                            <UserX className="size-4" />
                          </Button>
                        </div>
                      }
                    />
                  ))}
                </div>
              </div>
            </Panel>

            <Panel id="messenger-discover" title="공개/비공개방 찾기" icon={Globe2}>
              <div className="space-y-3 p-3">
                <div className="flex gap-2">
                  <Input
                    value={roomSearch}
                    onChange={(event) => setRoomSearch(event.target.value)}
                    placeholder="채팅방 검색"
                    onKeyDown={(event) => {
                      if (event.key === "Enter") void refreshDiscoverRooms();
                    }}
                  />
                  <Button type="button" size="icon" variant="outline" onClick={() => void refreshDiscoverRooms()} disabled={busy} aria-label="채팅방 검색">
                    <Search className="size-4" />
                  </Button>
                </div>
                <div className="space-y-2">
                  {discoverRooms.length === 0 ? (
                    <div className="rounded-md border border-border bg-background p-4 text-sm text-muted-foreground">참가 가능한 방이 없습니다.</div>
                  ) : discoverRooms.map((room) => (
                    <div key={room.id} className="rounded-md border border-border bg-background p-3">
                      <div className="flex min-w-0 items-start justify-between gap-2">
                        <div className="min-w-0">
                          <div className="flex min-w-0 items-center gap-2">
                            {room.locked ? <Lock className="size-4 shrink-0 text-muted-foreground" /> : <Globe2 className="size-4 shrink-0 text-muted-foreground" />}
                            <div className="truncate text-sm font-semibold text-foreground">{room.displayName}</div>
                          </div>
                          <div className="mt-1 line-clamp-2 text-xs text-muted-foreground">{room.description || `${room.memberCount}명 참여 중`}</div>
                        </div>
                        <Badge variant="outline">{conversationTypeLabel(room.type)}</Badge>
                      </div>
                      {!room.joined && room.locked && (
                        <Input
                          type="password"
                          className="mt-3"
                          value={joinPasswords[room.id] ?? ""}
                          onChange={(event) => setJoinPasswords((current) => ({ ...current, [room.id]: event.target.value }))}
                          placeholder="비밀번호"
                        />
                      )}
                      <Button
                        type="button"
                        size="sm"
                        variant={room.joined ? "outline" : "default"}
                        className="mt-3 w-full"
                        onClick={() => (room.joined ? setActiveConversationId(room.id) : joinRoom(room))}
                        disabled={busy}
                      >
                        {room.joined ? "열기" : "참가"}
                      </Button>
                    </div>
                  ))}
                </div>
              </div>
            </Panel>
          </div>

          <Panel
            title={activeConversation?.displayName ?? "대화"}
            icon={MessageSquare}
            action={activeConversation && (
              <div className="flex items-center gap-2">
                <Badge variant="outline">{conversationTypeLabel(activeConversation.type)}</Badge>
                {activeConversation.locked && <Lock className="size-4 text-muted-foreground" />}
                {activeConversation.type !== "DIRECT" && activeConversation.canManageRoom && (
                  <Button
                    type="button"
                    size="icon"
                    variant="outline"
                    onClick={() => setSettingsOpen(true)}
                    disabled={busy}
                    aria-label="방 설정"
                    title="방 설정 — 개설자 또는 방 관리자만"
                  >
                    <Settings2 className="size-4" />
                  </Button>
                )}
                <Button
                  type="button"
                  size="icon"
                  variant={activeConversation.muted ? "default" : "outline"}
                  onClick={() => toggleMute(activeConversation)}
                  disabled={busy}
                  aria-label={activeConversation.muted ? "채팅방 알림 켜기" : "채팅방 알림 해제"}
                  title={MUTE_HELP}
                >
                  {activeConversation.muted ? <BellOff className="size-4" /> : <Bell className="size-4" />}
                </Button>
                {activeConversation.type === "DIRECT" && activeConversation.peer && (
                  <Button
                    type="button"
                    size="icon"
                    variant="outline"
                    className="text-destructive"
                    onClick={() => blockUserQuietly(activeConversation.peer!)}
                    disabled={busy}
                    aria-label="대화 상대 차단"
                    title="대화 상대 차단"
                  >
                    <UserX className="size-4" />
                  </Button>
                )}
                <Button
                  type="button"
                  size="icon"
                  variant="outline"
                  className="text-destructive"
                  onClick={() => blockRoomQuietly(activeConversation)}
                  disabled={busy}
                  aria-label="이 채팅방 차단"
                  title="이 채팅방 차단 — 방 숨김 + 관련 초대 차단"
                >
                  <Ban className="size-4" />
                </Button>
              </div>
            )}
          >
            <div className="flex min-h-[680px] flex-col">
              {activeConversation ? (
                <>
                  <div className="border-b border-border px-4 py-3">
                    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <span>{activeConversation.memberCount}명</span>
                      <span>·</span>
                      <span>{formatDateTime(activeConversation.updatedAt)}</span>
                      {activeConversation.muted && (
                        <>
                          <span>·</span>
                          <span className="inline-flex items-center gap-1" title={MUTE_HELP}>
                            <BellOff className="size-3.5" />
                            알림 해제됨
                          </span>
                        </>
                      )}
                      {activeConversation.description && (
                        <>
                          <span>·</span>
                          <span className="min-w-0 truncate">{activeConversation.description}</span>
                        </>
                      )}
                    </div>
                  </div>

                  <div className="max-h-[520px] flex-1 space-y-3 overflow-y-auto bg-muted/20 p-4">
                    {messages.length === 0 ? (
                      <div className="flex h-48 items-center justify-center rounded-md border border-dashed border-border bg-background text-sm text-muted-foreground">
                        첫 메시지를 보내보세요.
                      </div>
                    ) : messages.map((message) => {
                      // 서버가 뷰어 기준으로 차단 처리한 메시지 — 톰스톤만 렌더("한 번 보기" 없음, 조용한 차단).
                      const tombstoned = (message as MessageResponse & BlockedMarker).blocked === true;
                      if (tombstoned) {
                        return (
                          <div key={message.id} className="flex justify-start">
                            <div className="max-w-[min(92%,720px)] rounded-lg border border-dashed border-border bg-muted/40 px-3 py-2">
                              <p className="text-sm italic text-muted-foreground">차단한 사용자의 메시지입니다.</p>
                            </div>
                          </div>
                        );
                      }
                      return (
                      <div key={message.id} className={cn("flex", message.mine ? "justify-end" : "justify-start")}>
                        <div
                          className={cn(
                            "max-w-[min(92%,720px)] rounded-lg border px-3 py-2",
                            message.mine ? "border-primary/30 bg-primary/10" : "border-border bg-background",
                          )}
                        >
                          <div className="mb-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                            <span className="font-semibold text-foreground">{message.mine ? "나" : userLabel(message.sender)}</span>
                            <span>{message.kind === "NOTE" ? "쪽지" : "채팅"}</span>
                            <span>{formatDateTime(message.createdAt)}</span>
                          </div>
                          {message.content && (
                            <p className="whitespace-pre-wrap break-words text-sm leading-6 text-foreground">{message.content}</p>
                          )}
                          {message.sharedPostings.length > 0 && (
                            <div className="mt-2 space-y-2">
                              {message.sharedPostings.map((posting) => (
                                <div key={posting.applicationCaseId} className="rounded-md border border-border bg-background/80 px-3 py-2">
                                  <div className="flex min-w-0 items-center gap-2 text-sm font-semibold">
                                    <Briefcase className="size-4 shrink-0 text-muted-foreground" />
                                    <span className="truncate">{posting.companyName} · {posting.jobTitle}</span>
                                  </div>
                                  <div className="mt-1 text-xs text-muted-foreground">
                                    마감 {posting.deadlineDate ?? "-"} · {posting.sourceType}
                                  </div>
                                </div>
                              ))}
                            </div>
                          )}
                          {message.attachments.length > 0 && (
                            <div className="mt-2 space-y-2">
                              {message.attachments.map((file) => (
                                <AttachmentButton key={file.fileId} file={file} onDownload={downloadAttachment} />
                              ))}
                            </div>
                          )}
                        </div>
                      </div>
                      );
                    })}
                  </div>

                  <div className="space-y-3 border-t border-border p-4">
                    <div className="flex flex-wrap gap-2">
                      {(["CHAT", "NOTE"] as MessageKind[]).map((kind) => (
                        <button
                          key={kind}
                          type="button"
                          onClick={() => setMessageKind(kind)}
                          className={cn(
                            "rounded-md border px-3 py-1.5 text-xs font-semibold",
                            messageKind === kind ? "border-primary bg-primary/10 text-primary" : "border-border text-muted-foreground hover:bg-accent",
                          )}
                        >
                          {kind === "CHAT" ? "채팅" : "쪽지"}
                        </button>
                      ))}
                    </div>
                    <Textarea
                      value={messageText}
                      onChange={(event) => setMessageText(event.target.value)}
                      placeholder="메시지를 입력하세요"
                      className="min-h-24"
                    />

                    <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_220px]">
                      <div className="space-y-2">
                        <div className="flex flex-wrap gap-2">
                          {SHARE_MODES.map((mode) => (
                            <button
                              key={mode.value}
                              type="button"
                              onClick={() => setShareMode(mode.value)}
                              className={cn(
                                "rounded-md border px-3 py-2 text-left text-xs",
                                shareMode === mode.value
                                  ? "border-primary bg-primary/10 text-primary"
                                  : "border-border text-muted-foreground hover:bg-accent",
                              )}
                            >
                              <div className="font-semibold">{SHARE_MODE_LABEL[mode.value]}</div>
                              <div>{mode.description}</div>
                            </button>
                          ))}
                        </div>
                        {shareMode === "TEMPORARY" && (
                          <div className="flex max-w-xs items-center gap-2">
                            <Input
                              type="number"
                              min={1}
                              max={720}
                              value={temporaryHours}
                              onChange={(event) => setTemporaryHours(Number(event.target.value) || 72)}
                            />
                            <span className="shrink-0 text-sm text-muted-foreground">시간 공유</span>
                          </div>
                        )}
                        <div className="flex flex-wrap items-center gap-2">
                          <label className="inline-flex h-9 cursor-pointer items-center justify-center gap-2 rounded-md border border-border bg-background px-3 text-sm font-medium hover:bg-accent">
                            <Paperclip className="size-4" />
                            파일 첨부
                            <input
                              type="file"
                              multiple
                              className="hidden"
                              onChange={handleFiles}
                            />
                          </label>
                          {pendingFiles.map((file) => (
                            <span key={`${file.id}-${file.originalName}`} className="inline-flex min-w-0 max-w-full items-center gap-1 rounded-md border border-border bg-background px-2 py-1 text-xs">
                              <span className="truncate">{file.originalName}</span>
                              <button
                                type="button"
                                onClick={() => setPendingFiles((current) => current.filter((item) => item !== file))}
                                aria-label="첨부 제거"
                              >
                                <X className="size-3.5" />
                              </button>
                            </span>
                          ))}
                        </div>
                      </div>

                      <div className="space-y-2">
                        <div className="text-xs font-semibold text-muted-foreground">공유할 공고</div>
                        <div className="max-h-36 space-y-1 overflow-y-auto rounded-md border border-border bg-background p-2">
                          {applicationCases.length === 0 ? (
                            <div className="px-2 py-3 text-xs text-muted-foreground">공유 가능한 지원 건이 없습니다.</div>
                          ) : applicationCases.slice(0, 10).map((applicationCase) => (
                            <button
                              key={applicationCase.id}
                              type="button"
                              onClick={() => toggleApplicationShare(applicationCase.id)}
                              className={cn(
                                "flex w-full min-w-0 items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs",
                                selectedApplicationIds.includes(applicationCase.id)
                                  ? "bg-primary/10 text-primary"
                                  : "text-muted-foreground hover:bg-accent",
                              )}
                            >
                              <Briefcase className="size-3.5 shrink-0" />
                              <span className="truncate">{applicationCase.companyName} · {applicationCase.jobTitle}</span>
                            </button>
                          ))}
                        </div>
                      </div>
                    </div>

                    <div className="flex justify-end">
                      <Button type="button" onClick={sendCurrentMessage} disabled={busy}>
                        <Send className="size-4" />
                        보내기
                      </Button>
                    </div>
                  </div>
                </>
              ) : (
                <div className="flex min-h-[520px] items-center justify-center p-6 text-center text-sm text-muted-foreground">
                  왼쪽에서 채팅방을 선택하거나 새 채팅방을 만들어 주세요.
                </div>
              )}
            </div>
          </Panel>
        </div>
      </div>

      {settingsOpen && activeConversation && activeConversation.type !== "DIRECT" && (
        <RoomSettingsSheet
          conversationId={activeConversation.id}
          onClose={() => setSettingsOpen(false)}
          onChanged={() => {
            // 방 설정 변경(제목/공개/공지/멤버 등)을 헤더·목록에 반영한다.
            void refreshConversations();
          }}
        />
      )}
    </main>
  );
}
