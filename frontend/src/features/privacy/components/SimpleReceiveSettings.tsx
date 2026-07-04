// ① 수신 범위(간편) — 표면별 레벨 셀렉터 (설계문서 §4-1, TikTok/Instagram 식).
// "모두/친구·기업만/친구만/아무도" 선택을 stranger/friend/company 3열 allow|block 묶음으로 변환해 PUT.
// 차단 계정/차단 IP 열은 건드리지 않는다. 현재 정책과 정확히 일치하지 않으면 "사용자 지정"으로 표시.
import { useState } from "react";
import { Inbox } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import { updatePrivacyPolicy } from "../api/privacyApi";
import {
  SURFACE_LABELS,
  defaultPolicyValue,
  resolveSurface,
  type PolicyValue,
  type PrivacyPolicyResponse,
} from "../types";

/** 간편 설정 대상 표면 6종 + 문구. */
const SIMPLE_SURFACES: Array<{ surface: string; question: string }> = [
  { surface: "dm", question: "1:1 채팅을 받을 사람" },
  { surface: "note", question: "쪽지를 받을 사람" },
  { surface: "friendRequest", question: "친구 요청을 받을 사람" },
  { surface: "invite", question: "채팅방 초대를 받을 사람" },
  { surface: "fileShare", question: "파일 공유를 받을 사람" },
  { surface: "postingShare", question: "공고 공유를 받을 사람" },
];

type ReceiveLevel = "all" | "friendCompany" | "friendOnly" | "none";

/** 레벨 → (stranger, friend, company) 허용 여부. */
const LEVEL_MAP: Record<ReceiveLevel, { stranger: PolicyValue; friend: PolicyValue; company: PolicyValue }> = {
  all: { stranger: "allow", friend: "allow", company: "allow" },
  friendCompany: { stranger: "block", friend: "allow", company: "allow" },
  friendOnly: { stranger: "block", friend: "allow", company: "block" },
  none: { stranger: "block", friend: "block", company: "block" },
};

const LEVEL_LABELS: Record<ReceiveLevel, string> = {
  all: "모두",
  friendCompany: "친구·기업만",
  friendOnly: "친구만",
  none: "아무도",
};

/** 현재 정책(오버라이드+기본값)에서 레벨 역산. 어느 조합에도 안 맞으면 null(사용자 지정). */
function deriveLevel(policy: PrivacyPolicyResponse, surface: string): ReceiveLevel | null {
  const current: Record<string, PolicyValue> = {};
  for (const relation of ["stranger", "friend", "company"] as const) {
    current[relation] = resolveSurface(policy.overrides[relation], surface) ?? defaultPolicyValue(relation);
  }
  for (const level of Object.keys(LEVEL_MAP) as ReceiveLevel[]) {
    const expected = LEVEL_MAP[level];
    if (
      expected.stranger === current.stranger &&
      expected.friend === current.friend &&
      expected.company === current.company
    ) {
      return level;
    }
  }
  return null;
}

export function SimpleReceiveSettings({
  policy,
  onPolicyChange,
  onError,
}: {
  policy: PrivacyPolicyResponse;
  onPolicyChange: (next: PrivacyPolicyResponse) => void;
  onError: (message: string) => void;
}) {
  const [savingSurface, setSavingSurface] = useState<string | null>(null);

  const applyLevel = async (surface: string, level: ReceiveLevel) => {
    setSavingSurface(surface);
    try {
      const values = LEVEL_MAP[level];
      const next = await updatePrivacyPolicy({
        relations: {
          stranger: { [surface]: values.stranger },
          friend: { [surface]: values.friend },
          company: { [surface]: values.company },
        },
      });
      onPolicyChange(next);
    } catch (err) {
      onError(err instanceof Error ? err.message : "수신 범위 저장에 실패했습니다.");
    } finally {
      setSavingSurface(null);
    }
  };

  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Inbox className="size-4 text-blue-600" />
          수신 범위
        </CardTitle>
        <p className="text-xs text-slate-500">
          누구에게서 받을지 한 번에 정합니다. 세부 조정은 아래 고급 매트릭스에서 할 수 있어요.
        </p>
      </CardHeader>
      <CardContent className="space-y-2">
        {SIMPLE_SURFACES.map(({ surface, question }) => {
          const level = deriveLevel(policy, surface);
          return (
            <div
              key={surface}
              className="flex flex-col gap-2 rounded-lg border border-slate-200 px-3 py-2.5 sm:flex-row sm:items-center sm:justify-between"
            >
              <div>
                <div className="text-sm font-semibold text-slate-800">{question}</div>
                <div className="text-xs text-slate-400">{SURFACE_LABELS[surface]}</div>
              </div>
              <Select
                value={level ?? "custom"}
                disabled={savingSurface === surface}
                onValueChange={(value) => {
                  if (value !== "custom") void applyLevel(surface, value as ReceiveLevel);
                }}
              >
                <SelectTrigger className="w-full sm:w-40" aria-label={`${question} 선택`}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(Object.keys(LEVEL_LABELS) as ReceiveLevel[]).map((key) => (
                    <SelectItem key={key} value={key}>
                      {LEVEL_LABELS[key]}
                    </SelectItem>
                  ))}
                  {/* 고급 매트릭스에서 조합을 바꾼 경우에만 나타나는 표시용 상태 */}
                  {level === null && (
                    <SelectItem value="custom" disabled>
                      사용자 지정
                    </SelectItem>
                  )}
                </SelectContent>
              </Select>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
