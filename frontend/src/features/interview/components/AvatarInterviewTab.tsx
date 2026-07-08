import { useState } from "react";
import { ArrowLeft, Sparkles, UserCircle2 } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card } from "@/app/components/ui/card";
import type { InterviewSession } from "../types/interview";
import { useDeviceCapabilities } from "../hooks/deviceCapabilities";
import { mediaUnsupportedReason } from "../hooks/mediaSupport";
import { AvatarTab } from "./AvatarTab";
import { LocalAvatarTab } from "./LocalAvatarTab";
import { DeviceHandoffCard, type HandoffReason } from "./DeviceHandoffCard";
import { RemoteMicConnectCard } from "./RemoteMicConnectCard";

type Mode = "premium" | "basic";

/**
 * 아바타 화상 면접 진입 — 방식(프리미엄/베이직) 선택 후 해당 면접으로.
 * 프리미엄 = 실시간 AI 면접관 아바타(HeyGen LiveAvatar, 무료 체험 1문제).
 * 베이직 = 브라우저 TTS + 셀프 녹화, 자체 AI 채점(외부 API 0, 전체 질문).
 * 요금제 게이팅(plan)은 E 요금제 DB 생성 후 — 현재는 사용자가 직접 선택(placeholder).
 */
export function AvatarInterviewTab({ session }: { session: InterviewSession | null }) {
  const [mode, setMode] = useState<Mode | null>(null);
  // 폰 카메라/마이크 핸드오프 스트림을 여기(부모)서 소유한다 — 프리미엄↔베이직 전환으로
  // 하위 탭이 언마운트돼도 폰 연결(WebRTC PeerConnection)이 끊기지 않게 하기 위함이다.
  const [remoteCam, setRemoteCam] = useState<MediaStream | null>(null);
  const deviceCaps = useDeviceCapabilities();

  // 이 기기에서 카메라/마이크가 없어(또는 비보안 오리진) 진행 불가한 원인 — 있으면 폰 핸드오프 안내.
  const mediaSupported =
    typeof navigator !== "undefined" &&
    !!navigator.mediaDevices &&
    typeof window !== "undefined" &&
    "MediaRecorder" in window;
  const handoffReason: HandoffReason | null = !mediaSupported
    ? (mediaUnsupportedReason() ?? "unsupported")
    : deviceCaps.hasCamera === false
      ? "no-camera"
      : deviceCaps.hasMicrophone === false
        ? "no-microphone"
        : null;

  if (!session) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-card p-10 text-center text-sm text-slate-400">
        "면접 모드 선택" 탭에서 지원 건과 모드를 고르고 면접을 시작하면 아바타 화상 면접을 진행할 수 있습니다.
      </div>
    );
  }

  if (mode) {
    return (
      <div className="space-y-3">
        <Button variant="ghost" size="sm" onClick={() => setMode(null)} className="gap-1.5 text-slate-500">
          <ArrowLeft className="size-4" /> 면접 방식 다시 선택
        </Button>
        {/* 폰 핸드오프 카드는 방식(프리미엄/베이직)보다 상위에서 한 번만 렌더한다 —
            프리미엄 연결 실패로 베이직으로 넘어가도 폰 연결이 유지되도록. */}
        {(handoffReason === "no-camera" || handoffReason === "no-microphone") && (
          <RemoteMicConnectCard sessionId={session.id} onStream={setRemoteCam} withVideo />
        )}
        {handoffReason && !remoteCam && (
          <DeviceHandoffCard sessionId={session.id} reason={handoffReason} />
        )}
        {mode === "premium" ? (
          <AvatarTab
            session={session}
            remoteCam={remoteCam}
            onFallbackToBasic={() => setMode("basic")}
          />
        ) : (
          <LocalAvatarTab session={session} remoteCam={remoteCam} />
        )}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-500">
        아바타 화상 면접 방식을 선택하세요. 두 방식 모두 준비된 질문으로 진행되고, 표정·음성·답변 내용을 채점합니다.
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <Card
          onClick={() => setMode("premium")}
          className="cursor-pointer border-slate-200 p-5 transition hover:border-purple-300 hover:shadow"
        >
          <div className="mb-2 flex items-center gap-2">
            <Sparkles className="size-5 text-purple-600" />
            <span className="font-semibold text-slate-800">실시간 아바타 면접</span>
            <Badge className="bg-purple-100 text-purple-700">프리미엄</Badge>
          </div>
          <p className="text-sm text-slate-500">
            <b>실제 AI 면접관 아바타</b>가 음성으로 질문합니다(HeyGen). 진짜 화상 면접 경험. 무료 체험은 1문제까지.
          </p>
        </Card>
        <Card
          onClick={() => setMode("basic")}
          className="cursor-pointer border-slate-200 p-5 transition hover:border-emerald-300 hover:shadow"
        >
          <div className="mb-2 flex items-center gap-2">
            <UserCircle2 className="size-5 text-emerald-600" />
            <span className="font-semibold text-slate-800">자체 AI 화상 면접</span>
            <Badge className="bg-emerald-100 text-emerald-700">베이직 · 무료</Badge>
          </div>
          <p className="text-sm text-slate-500">
            면접관이 질문을 <b>읽어주면 웹캠으로 녹화</b>됩니다. 자체 AI가 표정·음성·답변 내용을 채점합니다. 외부 API 없이(무료) 전체 질문 진행.
          </p>
        </Card>
      </div>
    </div>
  );
}
