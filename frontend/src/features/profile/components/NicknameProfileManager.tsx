import { useCallback, useEffect, useState } from "react";
import { Check, Pencil, Plus, RefreshCw, Star, Trash2, UserCircle2, X } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import {
  createNicknameProfile,
  deleteNicknameProfile,
  listNicknameProfiles,
  setDefaultNicknameProfile,
  updateNicknameProfile,
} from "../api/nicknameProfileApi";
import type { NicknameProfile, NicknameProfilePayload } from "../types/nicknameProfile";

interface DraftState {
  nickname: string;
  bio: string;
}

const emptyDraft: DraftState = { nickname: "", bio: "" };

/**
 * 닉네임 프로필 관리 카드 — 추가/수정/삭제/기본 지정.
 *
 * 한 계정이 여러 표시용 닉네임 프로필을 보유하고, 커뮤니티/채팅 작성 시 선택한다.
 * 제재/신고/차단은 계정 단위이며, 프로필은 표시 계층일 뿐임을 안내한다.
 */
export function NicknameProfileManager() {
  const [profiles, setProfiles] = useState<NicknameProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // 편집 대상: "new" = 새 프로필, number = 기존 프로필 id, null = 편집 안 함
  const [editing, setEditing] = useState<"new" | number | null>(null);
  const [draft, setDraft] = useState<DraftState>(emptyDraft);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setProfiles(await listNicknameProfiles());
    } catch (e) {
      setError(e instanceof Error ? e.message : "닉네임 프로필을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const startNew = () => {
    setDraft(emptyDraft);
    setEditing("new");
    setError(null);
    setMessage(null);
  };

  const startEdit = (profile: NicknameProfile) => {
    setDraft({ nickname: profile.nickname, bio: profile.bio ?? "" });
    setEditing(profile.id);
    setError(null);
    setMessage(null);
  };

  const cancelEdit = () => {
    setEditing(null);
    setDraft(emptyDraft);
  };

  const submit = async () => {
    const nickname = draft.nickname.trim();
    if (!nickname) {
      setError("닉네임을 입력해 주세요.");
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload: NicknameProfilePayload = { nickname, bio: draft.bio.trim() || null };
      if (editing === "new") {
        await createNicknameProfile(payload);
        setMessage("새 닉네임 프로필을 만들었습니다.");
      } else if (typeof editing === "number") {
        await updateNicknameProfile(editing, payload);
        setMessage("닉네임 프로필을 수정했습니다.");
      }
      cancelEdit();
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const remove = async (profile: NicknameProfile) => {
    if (!window.confirm(`'${profile.nickname}' 프로필을 삭제할까요? 작성한 글/댓글의 표시명은 유지됩니다.`)) return;
    setError(null);
    setMessage(null);
    try {
      await deleteNicknameProfile(profile.id);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "삭제에 실패했습니다.");
    }
  };

  const makeDefault = async (profile: NicknameProfile) => {
    setError(null);
    setMessage(null);
    try {
      await setDefaultNicknameProfile(profile.id);
      await load();
      setMessage(`'${profile.nickname}' 프로필을 기본으로 지정했습니다.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "기본 지정에 실패했습니다.");
    }
  };

  return (
    <Card className="border-slate-200">
      <CardHeader className="flex flex-row items-center justify-between gap-3">
        <div>
          <CardTitle className="flex items-center gap-2 text-base">
            <UserCircle2 className="size-5 text-blue-600" />
            닉네임 프로필 관리
          </CardTitle>
          <p className="mt-1 text-sm text-slate-500">
            커뮤니티/채팅에서 사용할 표시용 닉네임을 여러 개 만들 수 있습니다. 신고·차단·제재는 계정 단위로 적용됩니다.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
          <Button size="sm" className="bg-blue-600 text-white hover:bg-blue-700" onClick={startNew} disabled={editing === "new"}>
            <Plus className="size-4" />
            추가
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        {message && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}

        {editing === "new" && (
          <ProfileEditor
            draft={draft}
            saving={saving}
            title="새 닉네임 프로필"
            onChange={setDraft}
            onSave={() => void submit()}
            onCancel={cancelEdit}
          />
        )}

        {loading && profiles.length === 0 ? (
          <p className="py-6 text-center text-sm text-slate-500">불러오는 중...</p>
        ) : profiles.length === 0 && editing !== "new" ? (
          <p className="py-6 text-center text-sm text-slate-500">아직 닉네임 프로필이 없습니다. 추가를 눌러 만들어 보세요.</p>
        ) : (
          profiles.map((profile) =>
            editing === profile.id ? (
              <ProfileEditor
                key={profile.id}
                draft={draft}
                saving={saving}
                title={`프로필 수정 — ${profile.nickname}`}
                onChange={setDraft}
                onSave={() => void submit()}
                onCancel={cancelEdit}
              />
            ) : (
              <div key={profile.id} className="rounded-lg border border-slate-200 p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-slate-900">{profile.nickname}</span>
                    {profile.isDefault && (
                      <Badge className="bg-blue-100 text-blue-700">
                        <Star className="mr-1 size-3 fill-blue-600 text-blue-600" />
                        기본
                      </Badge>
                    )}
                  </div>
                  <div className="flex gap-1.5">
                    {!profile.isDefault && (
                      <Button variant="ghost" size="sm" onClick={() => void makeDefault(profile)} title="기본으로 지정">
                        <Star className="size-4 text-amber-500" />
                        기본
                      </Button>
                    )}
                    <Button variant="ghost" size="sm" onClick={() => startEdit(profile)}>
                      <Pencil className="size-4 text-slate-500" />
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => void remove(profile)}>
                      <Trash2 className="size-4 text-red-500" />
                    </Button>
                  </div>
                </div>
                {profile.bio && <p className="mt-1 text-sm text-slate-500">{profile.bio}</p>}
              </div>
            ),
          )
        )}
      </CardContent>
    </Card>
  );
}

function ProfileEditor({
  draft,
  saving,
  title,
  onChange,
  onSave,
  onCancel,
}: {
  draft: DraftState;
  saving: boolean;
  title: string;
  onChange(next: DraftState): void;
  onSave(): void;
  onCancel(): void;
}) {
  return (
    <div className="space-y-3 rounded-lg border border-blue-200 bg-blue-50/40 p-4">
      <div className="text-sm font-bold text-slate-800">{title}</div>
      <label className="block space-y-1">
        <span className="text-sm font-medium text-slate-700">닉네임 *</span>
        <Input
          value={draft.nickname}
          maxLength={30}
          onChange={(e) => onChange({ ...draft, nickname: e.target.value })}
          placeholder="전역에서 중복되지 않는 표시용 닉네임"
        />
      </label>
      <label className="block space-y-1">
        <span className="text-sm font-medium text-slate-700">한 줄 소개</span>
        <Textarea
          value={draft.bio}
          maxLength={200}
          rows={2}
          onChange={(e) => onChange({ ...draft, bio: e.target.value })}
          placeholder="프로필에 함께 노출할 짧은 소개 (선택)"
        />
      </label>
      <div className="flex justify-end gap-2">
        <Button variant="outline" size="sm" onClick={onCancel} disabled={saving}>
          <X className="size-4" />
          취소
        </Button>
        <Button size="sm" className="bg-blue-600 text-white hover:bg-blue-700" onClick={onSave} disabled={saving}>
          <Check className="size-4" />
          {saving ? "저장 중..." : "저장"}
        </Button>
      </div>
    </div>
  );
}
