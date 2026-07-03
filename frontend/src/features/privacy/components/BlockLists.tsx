// ③ 차단 목록 — 탭 3개(계정/IP/채팅방) (설계문서 §4-3, Facebook 식 하드블록 + 항목별 완화 시트).
//  - 계정: 검색으로 차단 추가(collaborationApi.searchUsers 재사용), 항목 클릭 → 표면별 완화 스위치·
//    "접속망(IP)도 차단" 토글·메모·차단 해제 시트.
//  - IP: 라벨/파생 계정/일치 계정 수만 표기(원본 IP 비노출), 해제만 가능.
//  - 채팅방: 파생 초대 차단 플래그 토글(각 익명 변형 포함 — 연속 초대 테러 방지), 해제.
import { useCallback, useEffect, useState } from "react";
import { Ban, Globe, MessageSquare, Search, ShieldOff, UserX, VenetianMask } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/app/components/ui/sheet";
import { Switch } from "@/app/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/app/components/ui/tabs";
import { searchUsers } from "@/features/collaboration/api/collaborationApi";
import type { CollaborationUser } from "@/features/collaboration/types/collaboration";
import {
  blockUser,
  deleteIpBlock,
  listConversationBlocks,
  listIpBlocks,
  listUserBlocks,
  unblockConversation,
  unblockUser,
  updateConversationBlock,
  updateUserBlock,
} from "../api/privacyApi";
import {
  BASE_SURFACES,
  ROOM_BLOCK_FLAGS,
  SURFACE_LABELS,
  resolveSurface,
  roomFlagDefault,
  type ConversationBlockResponse,
  type IpBlockResponse,
  type UserBlockResponse,
} from "../types";

function formatDate(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
}

const ROOM_TYPE_LABELS: Record<string, string> = {
  DIRECT: "1:1",
  GROUP: "단체",
  PUBLIC: "공개",
  PRIVATE: "비공개",
};

export function BlockLists({
  blockedAccountEffective,
  onError,
}: {
  /** 관계 정책의 blockedAccount 실효값 — 시트 스위치의 "상위 따름" 기본 상태 계산용. */
  blockedAccountEffective: Record<string, string>;
  onError: (message: string) => void;
}) {
  const [userBlocks, setUserBlocks] = useState<UserBlockResponse[]>([]);
  const [ipBlocks, setIpBlocks] = useState<IpBlockResponse[]>([]);
  const [conversationBlocks, setConversationBlocks] = useState<ConversationBlockResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState<CollaborationUser[]>([]);
  const [openBlockId, setOpenBlockId] = useState<number | null>(null);

  const openBlock = userBlocks.find((block) => block.id === openBlockId) ?? null;

  const reload = useCallback(async () => {
    try {
      const [users, ips, conversations] = await Promise.all([
        listUserBlocks(),
        listIpBlocks(),
        listConversationBlocks(),
      ]);
      setUserBlocks(users);
      setIpBlocks(ips);
      setConversationBlocks(conversations);
    } catch (err) {
      onError(err instanceof Error ? err.message : "차단 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [onError]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    try {
      await action();
    } catch (err) {
      onError(err instanceof Error ? err.message : "요청을 처리하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const search = () =>
    run(async () => {
      setResults(keyword.trim() ? await searchUsers(keyword.trim(), 10) : []);
    });

  const addUserBlock = (target: CollaborationUser) =>
    run(async () => {
      await blockUser({ targetUserId: target.id });
      setResults((current) => current.filter((user) => user.id !== target.id));
      await reload();
    });

  const patchUserBlock = (blockId: number, request: Parameters<typeof updateUserBlock>[1]) =>
    run(async () => {
      const updated = await updateUserBlock(blockId, request);
      setUserBlocks((current) => current.map((block) => (block.id === updated.id ? updated : block)));
      if (request.blockIp !== undefined) setIpBlocks(await listIpBlocks());
    });

  const removeUserBlock = (blockId: number) =>
    run(async () => {
      await unblockUser(blockId);
      setOpenBlockId(null);
      await reload();
    });

  const removeIpBlock = (ipBlockId: number) =>
    run(async () => {
      await deleteIpBlock(ipBlockId);
      setIpBlocks((current) => current.filter((block) => block.id !== ipBlockId));
    });

  const patchConversationBlock = (blockId: number, flags: Record<string, string>) =>
    run(async () => {
      const updated = await updateConversationBlock(blockId, flags);
      setConversationBlocks((current) => current.map((block) => (block.id === updated.id ? updated : block)));
    });

  const removeConversationBlock = (blockId: number) =>
    run(async () => {
      await unblockConversation(blockId);
      setConversationBlocks((current) => current.filter((block) => block.id !== blockId));
    });

  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Ban className="size-4 text-red-600" />
          차단 목록
        </CardTitle>
        <p className="text-xs text-slate-500">
          차단은 상대에게 알려지지 않습니다(조용한 차단). 계정 항목을 열면 표면별로 완화할 수 있어요.
        </p>
      </CardHeader>
      <CardContent>
        <Tabs defaultValue="users">
          <TabsList className="h-auto border border-slate-200 bg-card p-1">
            <TabsTrigger value="users">계정 ({userBlocks.length})</TabsTrigger>
            <TabsTrigger value="ips">IP ({ipBlocks.length})</TabsTrigger>
            <TabsTrigger value="conversations">채팅방 ({conversationBlocks.length})</TabsTrigger>
          </TabsList>

          {/* ── 계정 ── */}
          <TabsContent value="users" className="mt-4 space-y-3">
            <div className="flex gap-2">
              <Input
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="차단할 사용자 검색 (이름 또는 이메일)"
                onKeyDown={(event) => {
                  if (event.key === "Enter") void search();
                }}
              />
              <Button type="button" size="icon" variant="outline" onClick={() => void search()} disabled={busy} aria-label="사용자 검색">
                <Search className="size-4" />
              </Button>
            </div>
            {results.length > 0 && (
              <div className="space-y-2">
                {results.map((user) => {
                  const alreadyBlocked = userBlocks.some((block) => block.blockedUserId === user.id);
                  return (
                    <div key={user.id} className="flex items-center justify-between gap-3 rounded-md border border-slate-200 px-3 py-2">
                      <div className="min-w-0">
                        <div className="truncate text-sm font-semibold text-slate-800">{user.name || user.email}</div>
                        <div className="truncate text-xs text-slate-400">{user.email}</div>
                      </div>
                      <Button type="button" size="sm" variant="outline" className="text-red-600" onClick={() => void addUserBlock(user)} disabled={busy || alreadyBlocked}>
                        <UserX className="size-4" />
                        {alreadyBlocked ? "차단됨" : "차단"}
                      </Button>
                    </div>
                  );
                })}
              </div>
            )}
            {loading ? (
              <div className="rounded-md border border-slate-200 p-4 text-sm text-slate-500">불러오는 중입니다.</div>
            ) : userBlocks.length === 0 ? (
              <div className="rounded-md border border-slate-200 p-4 text-sm text-slate-500">차단한 계정이 없습니다.</div>
            ) : (
              <div className="space-y-2">
                {userBlocks.map((block) => (
                  <button
                    key={block.id}
                    type="button"
                    onClick={() => setOpenBlockId(block.id)}
                    className="flex w-full items-center justify-between gap-3 rounded-md border border-slate-200 px-3 py-2.5 text-left hover:bg-slate-50"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        {/* 익명 콘텐츠 기반 차단 — 실명/이메일 대신 회색 익명 아이콘 + 마스킹 라벨만 표시 */}
                        {block.masked && <VenetianMask className="size-4 shrink-0 text-slate-400" aria-label="익명 작성자" />}
                        <span className={`truncate text-sm font-semibold ${block.masked ? "text-slate-500" : "text-slate-800"}`}>
                          {block.blockedUserName ?? `사용자 #${block.blockedUserId}`}
                        </span>
                        {block.blockIp && <Badge className="bg-orange-100 text-orange-700">IP 차단</Badge>}
                        {Object.keys(block.flags).length > 0 && <Badge className="bg-slate-100 text-slate-600">부분 완화</Badge>}
                      </div>
                      <div className="truncate text-xs text-slate-400">
                        {block.masked ? "작성자가 누구인지는 표시되지 않습니다" : block.blockedUserEmail ?? ""} · {formatDate(block.createdAt)} 차단
                        {block.memo ? ` · ${block.memo}` : ""}
                      </div>
                    </div>
                    <span className="shrink-0 text-xs text-blue-600">세부 조정</span>
                  </button>
                ))}
              </div>
            )}
          </TabsContent>

          {/* ── IP ── */}
          <TabsContent value="ips" className="mt-4 space-y-3">
            <div className="rounded-md border border-blue-100 bg-blue-50 px-3 py-2 text-xs text-blue-700">
              보안을 위해 원본 IP 주소는 표시되지 않습니다. 차단한 계정의 최근 접속망을 해시로만 저장하며,
              같은 접속망의 다른 계정도 "차단한 IP" 정책으로 평가됩니다.
            </div>
            {ipBlocks.length === 0 ? (
              <div className="rounded-md border border-slate-200 p-4 text-sm text-slate-500">차단한 IP가 없습니다. 계정 차단 시트에서 "접속망(IP)도 차단"으로 추가됩니다.</div>
            ) : (
              ipBlocks.map((block) => (
                <div key={block.id} className="flex items-center justify-between gap-3 rounded-md border border-slate-200 px-3 py-2.5">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 text-sm font-semibold text-slate-800">
                      <Globe className="size-4 shrink-0 text-slate-400" />
                      <span className="truncate">{block.label}</span>
                    </div>
                    <div className="text-xs text-slate-400">
                      {block.sourceUserName ? `${block.sourceUserName} 계정에서 파생` : "직접 추가"} · 현재 계정 {block.matchedAccounts}개 일치 · {formatDate(block.createdAt)}
                    </div>
                  </div>
                  <Button type="button" size="sm" variant="outline" onClick={() => void removeIpBlock(block.id)} disabled={busy}>
                    <ShieldOff className="size-4" />
                    해제
                  </Button>
                </div>
              ))
            )}
          </TabsContent>

          {/* ── 채팅방 ── */}
          <TabsContent value="conversations" className="mt-4 space-y-3">
            {conversationBlocks.length === 0 ? (
              <div className="rounded-md border border-slate-200 p-4 text-sm text-slate-500">
                차단한 채팅방이 없습니다. 메신저의 방 헤더 메뉴에서 "이 채팅방 차단"으로 추가할 수 있어요.
              </div>
            ) : (
              conversationBlocks.map((block) => (
                <div key={block.id} className="space-y-3 rounded-md border border-slate-200 p-3">
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <MessageSquare className="size-4 shrink-0 text-slate-400" />
                        <span className="truncate text-sm font-semibold text-slate-800">
                          {block.conversationTitle ?? `채팅방 #${block.conversationId}`}
                        </span>
                        <Badge variant="outline">{ROOM_TYPE_LABELS[block.conversationType ?? ""] ?? block.conversationType ?? "-"}</Badge>
                      </div>
                      <div className="text-xs text-slate-400">방 숨김 + 관련 초대 차단 · {formatDate(block.createdAt)}</div>
                    </div>
                    <Button type="button" size="sm" variant="outline" onClick={() => void removeConversationBlock(block.id)} disabled={busy}>
                      <ShieldOff className="size-4" />
                      해제
                    </Button>
                  </div>
                  <div className="rounded-md bg-slate-50 px-3 py-2 text-xs text-slate-500">
                    아래 파생 규칙은 이 방 구성원을 통한 연속 초대 테러를 막기 위한 옵션입니다.
                  </div>
                  <div className="space-y-2">
                    {ROOM_BLOCK_FLAGS.map((flag) => (
                      <div key={flag.key} className="space-y-1.5 rounded-md border border-slate-100 px-3 py-2">
                        {[flag.key, `${flag.key}.anonymous`].map((key) => {
                          const anonymous = key.endsWith(".anonymous");
                          const effective = resolveSurface(block.flags, key) ?? roomFlagDefault(flag.key);
                          return (
                            <label key={key} className="flex items-center justify-between gap-3">
                              <span className="text-xs text-slate-600">
                                {flag.label}
                                {anonymous && " (익명 초대)"}
                                {!anonymous && <span className="block text-[11px] text-slate-400">{flag.help}</span>}
                              </span>
                              <Switch
                                checked={effective === "block"}
                                disabled={busy}
                                onCheckedChange={(checked) =>
                                  void patchConversationBlock(block.id, { [key]: checked ? "block" : "allow" })
                                }
                                aria-label={`${flag.label}${anonymous ? " 익명" : ""}`}
                              />
                            </label>
                          );
                        })}
                      </div>
                    ))}
                  </div>
                </div>
              ))
            )}
          </TabsContent>
        </Tabs>

        {/* ── 계정 차단 세부 시트 ── */}
        <Sheet open={openBlock !== null} onOpenChange={(open) => !open && setOpenBlockId(null)}>
          <SheetContent className="overflow-y-auto sm:max-w-md">
            {openBlock && (
              <UserBlockSheetBody
                block={openBlock}
                blockedAccountEffective={blockedAccountEffective}
                busy={busy}
                onPatch={(request) => void patchUserBlock(openBlock.id, request)}
                onUnblock={() => void removeUserBlock(openBlock.id)}
              />
            )}
          </SheetContent>
        </Sheet>
      </CardContent>
    </Card>
  );
}

/** 계정 차단 항목 시트 — 기본 전체 차단에서 표면 단위로 완화(스위치 ON=허용 명시, OFF=상위 따름 복원). */
function UserBlockSheetBody({
  block,
  blockedAccountEffective,
  busy,
  onPatch,
  onUnblock,
}: {
  block: UserBlockResponse;
  blockedAccountEffective: Record<string, string>;
  busy: boolean;
  onPatch: (request: { flags?: Record<string, string>; blockIp?: boolean; memo?: string }) => void;
  onUnblock: () => void;
}) {
  const [memo, setMemo] = useState(block.memo ?? "");

  useEffect(() => {
    setMemo(block.memo ?? "");
  }, [block.id, block.memo]);

  return (
    <>
      <SheetHeader>
        <SheetTitle>{block.blockedUserName ?? `사용자 #${block.blockedUserId}`} 차단 설정</SheetTitle>
        <SheetDescription>
          기본은 전체 차단입니다. 허용으로 켠 표면만 예외로 열립니다(예: 쪽지만 허용). 상대에게는 알려지지 않아요.
        </SheetDescription>
      </SheetHeader>
      <div className="space-y-4 px-4 pb-6">
        <div className="space-y-1.5">
          {BASE_SURFACES.map((surface) => {
            const explicit = resolveSurface(block.flags, surface);
            const effective = explicit ?? (blockedAccountEffective[surface] === "allow" ? "allow" : "block");
            return (
              <label key={surface} className="flex items-center justify-between gap-3 rounded-md border border-slate-100 px-3 py-2">
                <span className="text-sm text-slate-700">
                  {SURFACE_LABELS[surface]}
                  {!explicit && <span className="ml-1 text-[11px] text-slate-400">(기본)</span>}
                </span>
                <Switch
                  checked={effective === "allow"}
                  disabled={busy}
                  onCheckedChange={(checked) => onPatch({ flags: { [surface]: checked ? "allow" : "" } })}
                  aria-label={`${SURFACE_LABELS[surface]} 허용`}
                />
              </label>
            );
          })}
        </div>

        <label className="flex items-center justify-between gap-3 rounded-md border border-orange-200 bg-orange-50 px-3 py-2.5">
          <span className="text-sm text-slate-700">
            이 계정의 접속망(IP)도 차단
            <span className="block text-[11px] text-slate-500">
              같은 접속망의 부계정을 선제 차단합니다. 원본 IP는 저장·노출되지 않습니다.
            </span>
          </span>
          <Switch
            checked={block.blockIp}
            disabled={busy}
            onCheckedChange={(checked) => onPatch({ blockIp: checked })}
            aria-label="접속망 IP 차단"
          />
        </label>

        <div className="space-y-2">
          <div className="text-xs font-semibold text-slate-500">메모</div>
          <div className="flex gap-2">
            <Input value={memo} maxLength={200} onChange={(event) => setMemo(event.target.value)} placeholder="차단 사유 메모 (선택)" />
            <Button type="button" size="sm" variant="outline" onClick={() => onPatch({ memo })} disabled={busy || memo === (block.memo ?? "")}>
              저장
            </Button>
          </div>
        </div>

        <Button type="button" variant="outline" className="w-full text-red-600 hover:bg-red-50" onClick={onUnblock} disabled={busy}>
          <ShieldOff className="size-4" />
          차단 해제
        </Button>
      </div>
    </>
  );
}
