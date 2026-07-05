import { useEffect, useState } from "react";
import { Settings2 } from "lucide-react";
import { toast } from "@/features/notification/components/toast";
import * as communityApi from "../api/communityApi";
import type { ReactionRetentionSettings, RetentionValue } from "../types/community";

const SETTING_LABELS: { key: keyof ReactionRetentionSettings; label: string; hint: string }[] = [
  { key: "recommend", label: "추천", hint: "글이 수정되면 내 추천을" },
  { key: "disrecommend", label: "비추천", hint: "글이 수정되면 내 비추천을" },
  { key: "like", label: "좋아요", hint: "글이 수정되면 내 좋아요를" },
  { key: "dislike", label: "싫어요", hint: "글이 수정되면 내 싫어요를" },
  { key: "bookmark", label: "즐겨찾기", hint: "글이 수정되면 내 즐겨찾기를" },
];

/**
 * 반응 유지/해지 설정 카드 — 내가 반응한 게시글이 '수정'되었을 때
 * 리액션을 유지(keep, 기본)할지 자동 해지(release)할지 종류별로 정한다.
 * 저장은 커뮤니티 소유 API(GET/PUT /api/community/reaction-settings).
 */
export function ReactionSettingsCard() {
  const [settings, setSettings] = useState<ReactionRetentionSettings | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    communityApi.getReactionSettings()
      .then(setSettings)
      .catch(() => setSettings(null));
  }, []);

  const handleChange = async (key: keyof ReactionRetentionSettings, value: RetentionValue) => {
    if (!settings || saving) return;
    const prev = settings;
    setSettings({ ...settings, [key]: value });
    setSaving(true);
    try {
      const updated = await communityApi.updateReactionSettings({ [key]: value });
      setSettings(updated);
      toast.success(value === "release"
        ? "저장했습니다. 앞으로 글이 수정되면 해당 반응이 자동 해지됩니다."
        : "저장했습니다. 글이 수정되어도 반응이 유지됩니다.");
    } catch {
      setSettings(prev);
      toast.error("설정 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  if (!settings) return null;

  return (
    <div className="ct-iblock">
      <div className="ct-iblock__head">
        <Settings2 />
        <h4>반응 유지 설정</h4>
        <span>글이 수정되었을 때 내 반응 처리</span>
      </div>
      <div className="ct-iblock__body" style={{ gridTemplateColumns: "1fr" }}>
        {SETTING_LABELS.map(({ key, label, hint }) => (
          <div
            key={key}
            style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}
          >
            <div>
              <div style={{ fontSize: 14, fontWeight: 600 }}>{label}</div>
              <div style={{ fontSize: 12, color: "var(--muted-foreground)" }}>{hint}</div>
            </div>
            <select
              className="ct-anonsel-select"
              style={{
                height: 32, padding: "0 8px", border: "1px solid var(--border)", borderRadius: 8,
                background: "var(--card)", color: "var(--foreground)", fontSize: 13, cursor: "pointer",
              }}
              value={settings[key]}
              disabled={saving}
              onChange={(e) => handleChange(key, e.target.value as RetentionValue)}
            >
              <option value="keep">유지</option>
              <option value="release">자동 해지</option>
            </select>
          </div>
        ))}
      </div>
    </div>
  );
}
