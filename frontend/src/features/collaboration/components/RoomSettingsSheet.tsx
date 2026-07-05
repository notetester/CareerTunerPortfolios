import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Ban,
  Check,
  Crown,
  Globe2,
  History,
  Lock,
  Settings2,
  Shield,
  UserMinus,
  UserX,
  Users,
  X,
} from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import { Badge } from "@/app/components/ui/badge";
import { cn } from "@/app/components/ui/utils";
import {
  banConversationMember,
  getConversationSettings,
  kickConversationMember,
  setInviteAllowList,
  unbanConversationMember,
  updateConversationSettings,
  updateMemberPermission,
} from "../api/collaborationApi";
import type {
  ConversationMemberDetail,
  ConversationSettingsResponse,
  InvitePolicy,
} from "../types/collaboration";

const INVITE_POLICY_LABEL: Record<InvitePolicy, string> = {
  OWNER_ONLY: "개설자만",
  MANAGERS: "관리자까지",
  SPECIFIC_MEMBERS: "지정 멤버만",
  ALL_MEMBERS: "모든 멤버",
};

const PERMISSION_FIELDS: Array<{ key: PermissionKey; label: string }> = [
  { key: "canInvite", label: "초대" },
  { key: "canKick", label: "강퇴" },
  { key: "canBan", label: "차단(밴)" },
  { key: "canSetPassword", label: "비밀번호" },
  { key: "canEditRoom", label: "방 편집" },
  { key: "canManageMembers", label: "멤버 관리" },
];

type PermissionKey =
  | "canInvite"
  | "canKick"
  | "canBan"
  | "canSetPassword"
  | "canEditRoom"
  | "canManageMembers";

type TabKey = "general" | "members" | "invite" | "bans" | "audit";

const TABS: Array<{ key: TabKey; label: string; icon: typeof Settings2 }> = [
  { key: "general", label: "일반", icon: Settings2 },
  { key: "members", label: "멤버·권한", icon: Users },
  { key: "invite", label: "초대·공개", icon: Globe2 },
  { key: "bans", label: "차단 목록", icon: Ban },
  { key: "audit", label: "활동 로그", icon: History },
];

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

const AUDIT_LABEL: Record<string, string> = {
  ROOM_UPDATED: "방 정보 수정",
  MANAGER_GRANTED: "관리자 지정",
  MANAGER_REVOKED: "관리자 해제",
  MEMBER_KICKED: "강퇴",
  MEMBER_BANNED: "차단(밴)",
  MEMBER_UNBANNED: "차단 해제",
  MEMBER_INVITED: "초대",
  MEMBER_INVITED_ANONYMOUS: "익명 초대",
  MEMBER_JOINED: "참가",
  PASSWORD_SET: "비밀번호 설정",
  PASSWORD_CLEARED: "비밀번호 해제",
  INVITE_ALLOW_UPDATED: "초대 허용 목록 변경",
  ADMIN_UNBANNED: "운영자 차단 해제",
};

function auditLabel(action: string): string {
  return AUDIT_LABEL[action] ?? action;
}

/**
 * 방 설정 시트 — OWNER 및 위임받은 MANAGER 만 진입.
 * 탭: 일반 / 멤버·권한 / 초대·공개 / 차단(ban) 목록 / 활동 로그.
 */
export function RoomSettingsSheet({
  conversationId,
  onClose,
  onChanged,
}: {
  conversationId: number;
  onClose: () => void;
  onChanged?: (settings: ConversationSettingsResponse) => void;
}) {
  const [settings, setSettings] = useState<ConversationSettingsResponse | null>(null);
  const [tab, setTab] = useState<TabKey>("general");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // 일반 탭 편집 상태
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [roomNotice, setRoomNotice] = useState("");
  const [maxMembers, setMaxMembers] = useState(100);
  const [password, setPassword] = useState("");

  const apply = useCallback((next: ConversationSettingsResponse) => {
    setSettings(next);
    setTitle(next.title ?? "");
    setDescription(next.description ?? "");
    setRoomNotice(next.notice ?? "");
    setMaxMembers(next.maxMembers);
    onChanged?.(next);
  }, [onChanged]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const next = await getConversationSettings(conversationId);
      apply(next);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [apply, conversationId]);

  useEffect(() => {
    void load();
  }, [load]);

  const run = useCallback(async (
    action: () => Promise<ConversationSettingsResponse>,
    success?: string,
  ) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const next = await action();
      apply(next);
      if (success) setNotice(success);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }, [apply]);

  const perm = settings?.myPermission;

  const nonOwnerMembers = useMemo(
    () => (settings?.members ?? []).filter((m) => m.role !== "OWNER"),
    [settings?.members],
  );

  if (loading) {
    return (
      <SheetShell onClose={onClose} title="방 설정">
        <div className="p-6 text-sm text-muted-foreground">방 설정을 불러오는 중입니다.</div>
      </SheetShell>
    );
  }

  if (!settings || !perm) {
    return (
      <SheetShell onClose={onClose} title="방 설정">
        <div className="p-6 text-sm text-destructive">{error ?? "방 설정을 열 권한이 없습니다."}</div>
      </SheetShell>
    );
  }

  const saveGeneral = () => run(async () => updateConversationSettings(conversationId, {
    title: title.trim() || undefined,
    description: description.trim(),
    notice: roomNotice.trim(),
    maxMembers,
  }), "방 정보를 저장했습니다.");

  const setPasswordValue = () => run(async () => updateConversationSettings(conversationId, {
    passwordAction: "SET",
    password: password.trim(),
  }), "비밀번호를 설정했습니다.").then(() => setPassword(""));

  const clearPassword = () => run(async () => updateConversationSettings(conversationId, {
    passwordAction: "CLEAR",
  }), "비밀번호를 해제했습니다.");

  const togglePublic = (toType: "PUBLIC" | "PRIVATE") =>
    run(async () => updateConversationSettings(conversationId, { type: toType }),
      toType === "PUBLIC" ? "공개방으로 전환했습니다." : "비공개방으로 전환했습니다.");

  const setInvitePolicy = (policy: InvitePolicy) =>
    run(async () => updateConversationSettings(conversationId, { invitePolicy: policy }),
      "초대 정책을 변경했습니다.");

  const toggleAnonymous = (field: "allowAnonymous" | "anonymousOnly", value: boolean) =>
    run(async () => updateConversationSettings(conversationId, { [field]: value }),
      "익명 정책을 변경했습니다.");

  const setManager = (member: ConversationMemberDetail, manager: boolean) =>
    run(async () => updateMemberPermission(conversationId, member.userId, {
      manager,
      canInvite: member.permission.canInvite,
      canKick: member.permission.canKick,
      canBan: member.permission.canBan,
      canSetPassword: member.permission.canSetPassword,
      canEditRoom: member.permission.canEditRoom,
      canManageMembers: member.permission.canManageMembers,
    }), manager ? "방 관리자로 지정했습니다." : "방 관리자 권한을 해제했습니다.");

  const togglePermission = (member: ConversationMemberDetail, key: PermissionKey) =>
    run(async () => updateMemberPermission(conversationId, member.userId, {
      manager: true,
      canInvite: key === "canInvite" ? !member.permission.canInvite : member.permission.canInvite,
      canKick: key === "canKick" ? !member.permission.canKick : member.permission.canKick,
      canBan: key === "canBan" ? !member.permission.canBan : member.permission.canBan,
      canSetPassword: key === "canSetPassword" ? !member.permission.canSetPassword : member.permission.canSetPassword,
      canEditRoom: key === "canEditRoom" ? !member.permission.canEditRoom : member.permission.canEditRoom,
      canManageMembers: key === "canManageMembers" ? !member.permission.canManageMembers : member.permission.canManageMembers,
    }));

  const kick = (member: ConversationMemberDetail) =>
    run(async () => kickConversationMember(conversationId, member.userId),
      `${member.displayName}님을 강퇴했습니다.`);

  const ban = (member: ConversationMemberDetail) =>
    run(async () => banConversationMember(conversationId, member.userId),
      `${member.displayName}님을 차단했습니다.`);

  const unban = (userId: number) =>
    run(async () => unbanConversationMember(conversationId, userId), "차단을 해제했습니다.");

  const toggleInviteAllow = (userId: number) => {
    const current = settings.inviteAllowUserIds;
    const next = current.includes(userId)
      ? current.filter((id) => id !== userId)
      : [...current, userId];
    return run(async () => setInviteAllowList(conversationId, next), "초대 허용 목록을 저장했습니다.");
  };

  return (
    <SheetShell onClose={onClose} title="방 설정" permission={perm.owner ? "개설자" : "방 관리자"}>
      {error && (
        <div className="mx-4 mt-3 rounded-md border border-destructive/25 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </div>
      )}
      {notice && (
        <div className="mx-4 mt-3 rounded-md border border-primary/20 bg-primary/10 px-3 py-2 text-sm text-primary">
          {notice}
        </div>
      )}

      <div className="flex flex-wrap gap-1 border-b border-border px-3 py-2">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setTab(item.key)}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold",
              tab === item.key ? "bg-primary/10 text-primary" : "text-muted-foreground hover:bg-accent",
            )}
          >
            <item.icon className="size-3.5" />
            {item.label}
          </button>
        ))}
      </div>

      <div className="max-h-[60vh] overflow-y-auto p-4">
        {tab === "general" && (
          <div className="space-y-4">
            <Field label="방 이름">
              <Input value={title} onChange={(e) => setTitle(e.target.value)} disabled={!perm.canEditRoom} />
            </Field>
            <Field label="설명">
              <Input value={description} onChange={(e) => setDescription(e.target.value)} disabled={!perm.canEditRoom} />
            </Field>
            <Field label="공지">
              <Textarea
                value={roomNotice}
                onChange={(e) => setRoomNotice(e.target.value)}
                disabled={!perm.canEditRoom}
                className="min-h-20"
                placeholder="방 공지를 입력하세요"
              />
            </Field>
            <Field label="인원수 제한">
              <Input
                type="number"
                min={2}
                max={1000}
                value={maxMembers}
                onChange={(e) => setMaxMembers(Number(e.target.value) || 2)}
                disabled={!perm.canEditRoom}
                className="max-w-40"
              />
            </Field>
            {perm.canEditRoom && (
              <Button type="button" onClick={saveGeneral} disabled={busy}>
                <Check className="size-4" />
                방 정보 저장
              </Button>
            )}
            <p className="text-xs text-muted-foreground">
              방 프로필 사진은 대화 입력창의 파일 첨부로 업로드한 뒤 지정할 수 있습니다.
            </p>
          </div>
        )}

        {tab === "members" && (
          <div className="space-y-3">
            {settings.members.map((member) => (
              <div key={member.userId} className="rounded-md border border-border bg-background p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-foreground">{member.displayName}</span>
                    {member.anonymous && <Badge variant="outline">익명</Badge>}
                    <RoleBadge role={member.role} />
                  </div>
                  {member.role !== "OWNER" && (
                    <div className="flex items-center gap-1">
                      {perm.canManageMembers && perm.owner && (
                        <Button
                          type="button"
                          size="sm"
                          variant={member.role === "MANAGER" ? "default" : "outline"}
                          onClick={() => setManager(member, member.role !== "MANAGER")}
                          disabled={busy}
                        >
                          <Shield className="size-3.5" />
                          {member.role === "MANAGER" ? "관리자 해제" : "관리자 지정"}
                        </Button>
                      )}
                      {perm.canKick && (
                        <Button
                          type="button"
                          size="icon"
                          variant="outline"
                          onClick={() => kick(member)}
                          disabled={busy}
                          aria-label="강퇴"
                          title="강퇴(재입장 가능)"
                        >
                          <UserMinus className="size-4" />
                        </Button>
                      )}
                      {perm.canBan && (
                        <Button
                          type="button"
                          size="icon"
                          variant="outline"
                          className="text-destructive"
                          onClick={() => ban(member)}
                          disabled={busy}
                          aria-label="차단(밴)"
                          title="차단(재입장 불가)"
                        >
                          <UserX className="size-4" />
                        </Button>
                      )}
                    </div>
                  )}
                </div>
                {member.role === "MANAGER" && perm.owner && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {PERMISSION_FIELDS.map((field) => (
                      <button
                        key={field.key}
                        type="button"
                        onClick={() => togglePermission(member, field.key)}
                        disabled={busy}
                        className={cn(
                          "rounded-md border px-2 py-1 text-xs",
                          member.permission[field.key]
                            ? "border-primary bg-primary/10 text-primary"
                            : "border-border text-muted-foreground hover:bg-accent",
                        )}
                      >
                        {field.label}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {tab === "invite" && (
          <div className="space-y-5">
            <Field label="공개 설정">
              <div className="flex gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant={settings.type === "PUBLIC" ? "default" : "outline"}
                  onClick={() => togglePublic("PUBLIC")}
                  disabled={busy || !perm.canEditRoom || settings.type === "DIRECT" || settings.type === "GROUP"}
                >
                  <Globe2 className="size-4" />
                  공개방
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant={settings.type === "PRIVATE" ? "default" : "outline"}
                  onClick={() => togglePublic("PRIVATE")}
                  disabled={busy || !perm.canEditRoom || settings.type === "DIRECT" || settings.type === "GROUP"}
                >
                  <Lock className="size-4" />
                  비공개방
                </Button>
              </div>
            </Field>

            {perm.canSetPassword && (
              <Field label="비밀번호">
                <div className="flex flex-wrap items-center gap-2">
                  <Input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder={settings.hasPassword ? "새 비밀번호" : "비밀번호 설정"}
                    className="max-w-52"
                  />
                  <Button type="button" size="sm" onClick={setPasswordValue} disabled={busy || !password.trim()}>
                    설정/변경
                  </Button>
                  {settings.hasPassword && (
                    <Button type="button" size="sm" variant="outline" onClick={clearPassword} disabled={busy}>
                      해제
                    </Button>
                  )}
                </div>
              </Field>
            )}

            <Field label="초대 권한 정책">
              <div className="flex flex-wrap gap-2">
                {(Object.keys(INVITE_POLICY_LABEL) as InvitePolicy[]).map((policy) => (
                  <button
                    key={policy}
                    type="button"
                    onClick={() => setInvitePolicy(policy)}
                    disabled={busy || !perm.canEditRoom}
                    className={cn(
                      "rounded-md border px-3 py-1.5 text-xs font-medium",
                      settings.invitePolicy === policy
                        ? "border-primary bg-primary/10 text-primary"
                        : "border-border text-muted-foreground hover:bg-accent",
                    )}
                  >
                    {INVITE_POLICY_LABEL[policy]}
                  </button>
                ))}
              </div>
            </Field>

            {settings.invitePolicy === "SPECIFIC_MEMBERS" && (
              <Field label="초대 가능한 멤버">
                <div className="space-y-1.5">
                  {nonOwnerMembers.map((member) => (
                    <button
                      key={member.userId}
                      type="button"
                      onClick={() => toggleInviteAllow(member.userId)}
                      disabled={busy || !perm.canEditRoom}
                      className={cn(
                        "flex w-full items-center justify-between rounded-md border px-3 py-2 text-sm",
                        settings.inviteAllowUserIds.includes(member.userId)
                          ? "border-primary bg-primary/10 text-primary"
                          : "border-border text-muted-foreground hover:bg-accent",
                      )}
                    >
                      <span>{member.displayName}</span>
                      {settings.inviteAllowUserIds.includes(member.userId) && <Check className="size-4" />}
                    </button>
                  ))}
                </div>
              </Field>
            )}

            <Field label="익명 참가">
              <div className="space-y-2">
                <ToggleRow
                  label="익명 참가 허용"
                  checked={settings.allowAnonymous}
                  disabled={busy || !perm.canEditRoom}
                  onToggle={(v) => toggleAnonymous("allowAnonymous", v)}
                />
                <ToggleRow
                  label="익명만 참가 가능"
                  checked={settings.anonymousOnly}
                  disabled={busy || !perm.canEditRoom}
                  onToggle={(v) => toggleAnonymous("anonymousOnly", v)}
                />
              </div>
            </Field>
          </div>
        )}

        {tab === "bans" && (
          <div className="space-y-2">
            {settings.bans.length === 0 ? (
              <div className="rounded-md border border-border bg-background p-4 text-sm text-muted-foreground">
                차단된 사용자가 없습니다.
              </div>
            ) : settings.bans.map((banned) => (
              <div key={banned.userId} className="flex items-center justify-between rounded-md border border-border bg-background px-3 py-2">
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-foreground">{banned.displayName}</div>
                  <div className="text-xs text-muted-foreground">
                    {formatDateTime(banned.bannedAt)}{banned.reason ? ` · ${banned.reason}` : ""}
                  </div>
                </div>
                {perm.canBan && (
                  <Button type="button" size="sm" variant="outline" onClick={() => unban(banned.userId)} disabled={busy}>
                    차단 해제
                  </Button>
                )}
              </div>
            ))}
          </div>
        )}

        {tab === "audit" && (
          <div className="space-y-2">
            {settings.recentAudits.length === 0 ? (
              <div className="rounded-md border border-border bg-background p-4 text-sm text-muted-foreground">
                아직 활동 기록이 없습니다.
              </div>
            ) : settings.recentAudits.map((audit) => (
              <div key={audit.id} className="rounded-md border border-border bg-background px-3 py-2">
                <div className="flex items-center gap-2 text-sm">
                  <Badge variant="outline">{auditLabel(audit.action)}</Badge>
                  <span className="font-medium text-foreground">{audit.actorName ?? "-"}</span>
                  {audit.targetName && (
                    <span className="text-muted-foreground">→ {audit.targetName}</span>
                  )}
                </div>
                <div className="mt-1 text-xs text-muted-foreground">
                  {formatDateTime(audit.createdAt)}{audit.detail ? ` · ${audit.detail}` : ""}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </SheetShell>
  );
}

function SheetShell({
  title,
  permission,
  onClose,
  children,
}: {
  title: string;
  permission?: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div
        className="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-lg border border-border bg-card shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <Settings2 className="size-4 text-primary" />
            {title}
            {permission && <Badge variant="outline">{permission}</Badge>}
          </h2>
          <button type="button" onClick={onClose} aria-label="닫기" className="text-muted-foreground hover:text-foreground">
            <X className="size-4" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <div className="text-xs font-semibold text-muted-foreground">{label}</div>
      {children}
    </div>
  );
}

function RoleBadge({ role }: { role: string }) {
  if (role === "OWNER") {
    return (
      <Badge className="gap-1">
        <Crown className="size-3" />
        개설자
      </Badge>
    );
  }
  if (role === "MANAGER") {
    return (
      <Badge variant="outline" className="gap-1">
        <Shield className="size-3" />
        관리자
      </Badge>
    );
  }
  return <Badge variant="outline">멤버</Badge>;
}

function ToggleRow({
  label,
  checked,
  disabled,
  onToggle,
}: {
  label: string;
  checked: boolean;
  disabled?: boolean;
  onToggle: (value: boolean) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onToggle(!checked)}
      disabled={disabled}
      className={cn(
        "flex w-full items-center justify-between rounded-md border px-3 py-2 text-sm",
        checked ? "border-primary bg-primary/10 text-primary" : "border-border text-muted-foreground hover:bg-accent",
      )}
    >
      <span>{label}</span>
      <span className={cn("inline-flex size-5 items-center justify-center rounded-full border", checked ? "border-primary bg-primary text-primary-foreground" : "border-border")}>
        {checked && <Check className="size-3.5" />}
      </span>
    </button>
  );
}
