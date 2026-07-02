import { Mic, Camera, Trash2, Hand } from "lucide-react";

/**
 * 마이크/카메라 권한 사전 동의 바텀시트 (스토어 심사 대응).
 * OS 권한 다이얼로그 전에 왜 필요한지·데이터 처리 방식을 설명한다.
 * - 원본 즉시 폐기, 거부 시 대체 경로(텍스트 답변) 고지
 * - 허용 시 localStorage 에 기억 → 다음부터 바로 진입
 */
export type PermKind = "voice" | "avatar";

const ACCEPT_KEY = (kind: PermKind) => `careertuner.perm.preprompt.${kind}`;

export function isPrepromptAccepted(kind: PermKind): boolean {
  try {
    return localStorage.getItem(ACCEPT_KEY(kind)) === "1";
  } catch {
    return false;
  }
}
export function markPrepromptAccepted(kind: PermKind): void {
  try {
    localStorage.setItem(ACCEPT_KEY(kind), "1");
  } catch {
    /* no-op */
  }
}

export function PermissionPreprompt({
  kind,
  open,
  onAllow,
  onClose,
}: {
  kind: PermKind;
  open: boolean;
  onAllow: () => void;
  onClose: () => void;
}) {
  if (!open) return null;
  const voice = kind === "voice";
  return (
    <div className="fixed inset-0 z-[70]">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-[2px]" onClick={onClose} />
      <div
        className="absolute inset-x-0 bottom-0 rounded-t-[20px] border-t border-white/10 bg-[#0a0a0c] px-5 pt-2.5 shadow-[0_-20px_60px_rgba(0,0,0,0.6),inset_0_1px_0_rgba(255,255,255,0.06)]"
        style={{ paddingBottom: "calc(env(safe-area-inset-bottom) + 24px)" }}
      >
        <div className="mx-auto mb-4 mt-1 h-1 w-9 rounded-full bg-white/15" />
        <div className="mx-auto mb-3.5 flex size-[52px] items-center justify-center rounded-[14px] border border-[#5E6AD2]/30 bg-[#5E6AD2]/15 text-[#7d88de] shadow-[0_0_30px_rgba(94,106,210,0.15)]">
          {voice ? <Mic className="size-6" /> : <Camera className="size-6" />}
        </div>
        <h3 className="text-center text-[16px] font-semibold tracking-tight text-[#EDEDEF]">
          {voice ? "마이크 사용 안내" : "카메라·마이크 사용 안내"}
        </h3>
        <p className="mt-2 mb-4 text-center text-[12.5px] leading-relaxed text-[#8A8F98]">
          {voice
            ? "음성 면접 답변을 녹음해 텍스트로 바꾸고 전달력을 채점합니다."
            : "화상 면접에서 표정·자세·음성을 분석해 비언어 피드백을 드립니다."}
        </p>
        <div className="mb-4 flex flex-col gap-2">
          <div className="flex items-start gap-3 rounded-xl border border-white/[0.06] bg-white/[0.04] px-3.5 py-3">
            <Trash2 className="mt-0.5 size-4 shrink-0 text-[#8A8F98]" />
            <div>
              <div className="text-[12.5px] font-semibold text-[#EDEDEF]">원본은 즉시 폐기</div>
              <div className="mt-0.5 text-[11.5px] leading-relaxed text-[#8A8F98]">
                채점 후 녹음·녹화 원본은 저장하지 않아요. 점수와 텍스트만 남습니다.
              </div>
            </div>
          </div>
          <div className="flex items-start gap-3 rounded-xl border border-white/[0.06] bg-white/[0.04] px-3.5 py-3">
            <Hand className="mt-0.5 size-4 shrink-0 text-[#8A8F98]" />
            <div>
              <div className="text-[12.5px] font-semibold text-[#EDEDEF]">언제든 거부 가능</div>
              <div className="mt-0.5 text-[11.5px] leading-relaxed text-[#8A8F98]">
                거부해도 텍스트 답변으로 면접을 계속할 수 있어요.
              </div>
            </div>
          </div>
        </div>
        <div className="flex gap-2">
          <button
            onClick={onClose}
            className="flex-1 rounded-[10px] border border-white/[0.06] py-3 text-[13.5px] font-semibold text-[#EDEDEF]"
          >
            나중에
          </button>
          <button
            onClick={() => {
              markPrepromptAccepted(kind);
              onAllow();
            }}
            className="flex-[2] rounded-[10px] bg-gradient-to-b from-[#7d88de] to-[#5E6AD2] py-3 text-[13.5px] font-semibold text-white shadow-[0_0_0_1px_rgba(94,106,210,0.5),0_4px_12px_rgba(94,106,210,0.3),inset_0_1px_0_rgba(255,255,255,0.2)]"
          >
            허용하고 시작
          </button>
        </div>
      </div>
    </div>
  );
}
