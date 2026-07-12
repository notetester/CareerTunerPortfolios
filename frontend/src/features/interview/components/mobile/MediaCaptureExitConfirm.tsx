import { useEffect, useId, useRef } from "react";

export function MediaCaptureExitConfirm({
  processing,
  onKeep,
  onDiscard,
}: {
  processing: boolean;
  onKeep: () => void;
  onDiscard: () => void;
}) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const keepButtonRef = useRef<HTMLButtonElement>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);
  const onKeepRef = useRef(onKeep);
  const titleId = useId();
  const descriptionId = useId();
  onKeepRef.current = onKeep;

  useEffect(() => {
    returnFocusRef.current = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    keepButtonRef.current?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onKeepRef.current();
        return;
      }
      if (event.key !== "Tab") return;
      const dialog = dialogRef.current;
      if (!dialog) return;
      const buttons = Array.from(dialog.querySelectorAll<HTMLButtonElement>("button:not([disabled])"));
      const first = buttons[0];
      const last = buttons.at(-1);
      if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKeyDown, true);
    return () => {
      document.removeEventListener("keydown", onKeyDown, true);
      if (returnFocusRef.current?.isConnected) returnFocusRef.current.focus();
    };
  }, []);

  return (
    <div
      role="alertdialog"
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      className="fixed inset-0 z-[70] flex items-center justify-center bg-black/70 px-5 backdrop-blur-sm"
    >
      <div ref={dialogRef} className="w-full max-w-sm rounded-2xl border border-white/10 bg-[#141517] p-5 text-[#f7f8f8] shadow-2xl">
        <h2 id={titleId} className="text-[16px] font-semibold">
          {processing ? "원본 준비를 중단할까요?" : "녹화를 종료할까요?"}
        </h2>
        <p id={descriptionId} className="mt-2 text-[13px] leading-relaxed text-[#c2c7d0]">
          준비 중인 새 원본은 저장되지 않습니다. 입력한 초안과 이미 전송 대기 중인 원본은 그대로 유지됩니다.
        </p>
        <div className="mt-5 flex gap-2">
          <button
            ref={keepButtonRef}
            type="button"
            onClick={onKeep}
            className="flex min-h-11 flex-1 items-center justify-center rounded-xl border border-white/10 bg-white/[0.06] px-4 text-[13px] font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#7170ff]"
          >
            {processing ? "계속 준비" : "계속 녹화"}
          </button>
          <button
            type="button"
            onClick={onDiscard}
            className="flex min-h-11 flex-1 items-center justify-center rounded-xl bg-[#d8635d] px-4 text-[13px] font-semibold text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#7170ff]"
          >
            새 원본 버리기
          </button>
        </div>
      </div>
    </div>
  );
}
