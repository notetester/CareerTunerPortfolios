import { useCallback, useEffect, useState } from "react";
import { Check, UserCircle2, VenetianMask } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import {
  getConversationProfile,
  listNicknameProfiles,
  setConversationProfile,
} from "../api/nicknameProfileApi";
import type { ConversationProfile, NicknameProfile } from "../types/nicknameProfile";

/**
 * 채팅방 전용 프로필 선택기.
 *
 * 특정 대화방에서 사용할 닉네임 프로필을 고르거나 익명으로 참가한다.
 * 익명 참가는 nicknameProfileId=null 로 전송된다(개인 차단 익명 표면과 정합).
 * 이 방에서만 유효하며, 다른 방/계정 단위 표시에는 영향을 주지 않는다.
 */
export function ChatroomProfilePicker({
  conversationId,
  onChanged,
}: {
  conversationId: number;
  onChanged?(profile: ConversationProfile): void;
}) {
  const [profiles, setProfiles] = useState<NicknameProfile[]>([]);
  const [current, setCurrent] = useState<ConversationProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [list, mapping] = await Promise.all([
        listNicknameProfiles(),
        getConversationProfile(conversationId),
      ]);
      setProfiles(list);
      setCurrent(mapping);
    } catch (e) {
      setError(e instanceof Error ? e.message : "채팅방 프로필을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [conversationId]);

  useEffect(() => {
    void load();
  }, [load]);

  const apply = async (nicknameProfileId: number | null) => {
    setSaving(true);
    setError(null);
    try {
      const next = await setConversationProfile(conversationId, nicknameProfileId);
      setCurrent(next);
      onChanged?.(next);
    } catch (e) {
      setError(e instanceof Error ? e.message : "변경에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  // resolved=true 이면서 프로필 미지정이면 익명 참가 상태
  const selectedProfileId = current?.resolved ? current.nicknameProfileId : undefined;
  const anonymousSelected = current?.resolved === true && current.anonymous;

  if (loading) {
    return <p className="py-3 text-center text-sm text-slate-500">채팅방 프로필 불러오는 중...</p>;
  }

  return (
    <div className="space-y-3">
      <div>
        <div className="text-sm font-bold text-slate-800">이 대화방에서 사용할 프로필</div>
        <p className="text-xs text-slate-500">이 방에서만 적용됩니다. 신고·차단·제재는 계정 단위로 유지됩니다.</p>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}

      <div className="space-y-2">
        {profiles.map((profile) => {
          const active = selectedProfileId === profile.id && !anonymousSelected;
          return (
            <Button
              key={profile.id}
              variant="outline"
              className={`h-auto w-full justify-between py-2.5 ${active ? "border-blue-500 bg-blue-50" : ""}`}
              onClick={() => void apply(profile.id)}
              disabled={saving}
            >
              <span className="flex items-center gap-2">
                <UserCircle2 className="size-4 text-blue-600" />
                <span className="font-medium">{profile.nickname}</span>
                {profile.isDefault && <span className="text-xs text-slate-400">(기본)</span>}
              </span>
              {active && <Check className="size-4 text-blue-600" />}
            </Button>
          );
        })}

        <Button
          variant="outline"
          className={`h-auto w-full justify-between py-2.5 ${anonymousSelected ? "border-slate-500 bg-slate-100" : ""}`}
          onClick={() => void apply(null)}
          disabled={saving}
        >
          <span className="flex items-center gap-2">
            <VenetianMask className="size-4 text-slate-500" />
            <span className="font-medium">익명으로 참가</span>
          </span>
          {anonymousSelected && <Check className="size-4 text-slate-600" />}
        </Button>
      </div>
    </div>
  );
}
