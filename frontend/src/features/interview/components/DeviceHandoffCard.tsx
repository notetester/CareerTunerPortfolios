import { useState } from "react";
import { Loader2, MicOff, ShieldAlert, Smartphone, VideoOff, type LucideIcon } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { toast } from "@/features/notification/components/toast";
import { dispatchToPhone } from "../api/deviceHandoffApi";

/**
 * 이 기기에서 면접을 진행할 수 없는 원인(카메라/마이크 없음, 비보안 오리진 등)을 안내하고
 * "폰으로 이어하기"(기기 핸드오프) 버튼을 제공하는 큰 안내 카드.
 * 성공 시 폰에 INTERVIEW_DISPATCH 알림이 가고, 탭하면 같은 세션이 폰에서 이어진다.
 */
export type HandoffReason = "no-camera" | "no-microphone" | "insecure-context" | "unsupported";

const REASON_COPY: Record<HandoffReason, { icon: LucideIcon; title: string; description: string }> = {
  "no-camera": {
    icon: VideoOff,
    title: "이 기기에 카메라가 없습니다",
    description:
      "화상 면접에는 카메라가 필요합니다. 폰으로 이어하면 폰 카메라로 이 세션을 바로 진행할 수 있습니다.",
  },
  "no-microphone": {
    icon: MicOff,
    title: "이 기기에 마이크가 없습니다",
    description:
      "답변 녹음에는 마이크가 필요합니다. 폰으로 이어하면 폰 마이크로 이 세션을 바로 진행할 수 있습니다.",
  },
  "insecure-context": {
    icon: ShieldAlert,
    title: "보안 연결(https 또는 앱)에서만 카메라/마이크를 쓸 수 있습니다",
    description:
      "현재 http(비보안) 주소로 접속되어 브라우저가 카메라/마이크 접근을 차단했습니다. https 로 접속하거나 폰 앱으로 이어하세요.",
  },
  unsupported: {
    icon: ShieldAlert,
    title: "이 브라우저에서는 녹음/녹화를 사용할 수 없습니다",
    description: "최신 Chrome/Edge 로 접속하거나, 폰으로 이어하면 이 세션을 바로 진행할 수 있습니다.",
  },
};

export function DeviceHandoffCard({
  sessionId,
  reason,
}: {
  sessionId: number;
  reason: HandoffReason;
}) {
  const [sending, setSending] = useState(false);
  const copy = REASON_COPY[reason];
  const Icon = copy.icon;

  const send = async () => {
    if (sending) return;
    setSending(true);
    try {
      await dispatchToPhone(sessionId);
      toast.success("폰 알림(최대 30초 내)을 탭하면 이 세션이 폰에서 이어집니다");
    } catch {
      toast.error("폰으로 보내기에 실패했습니다. 네트워크 연결을 확인해 주세요.");
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="rounded-xl border border-indigo-200 bg-indigo-50/60 p-6 text-center">
      <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-white text-[#6366f1] shadow-sm">
        <Icon className="size-6" />
      </div>
      <p className="mt-3 text-sm font-bold text-slate-800">{copy.title}</p>
      <p className="mx-auto mt-1 max-w-md text-sm leading-6 text-slate-500">{copy.description}</p>
      <Button
        onClick={() => void send()}
        disabled={sending}
        className="mt-4 gap-1.5 bg-indigo-600 hover:bg-indigo-700"
      >
        {sending ? <Loader2 className="size-4 animate-spin" /> : <Smartphone className="size-4" />}
        폰으로 이어하기
      </Button>
    </div>
  );
}
