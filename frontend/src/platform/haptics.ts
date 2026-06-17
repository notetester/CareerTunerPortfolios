/**
 * 햅틱(진동) 피드백 어댑터.
 * 네이티브: Capacitor Haptics 플러그인(있으면) → 웹: navigator.vibrate → 미지원: no-op.
 */
import { isNativeApp, nativePlugin } from "./capacitor";

type HapticStyle = "light" | "medium" | "heavy";

interface CapHaptics {
  impact: (opts: { style: string }) => Promise<void>;
}

export function haptic(style: HapticStyle = "light"): void {
  try {
    if (isNativeApp()) {
      const h = nativePlugin<CapHaptics>("Haptics");
      if (h) {
        void h.impact({ style: style.toUpperCase() });
        return;
      }
    }
    if (typeof navigator !== "undefined" && typeof navigator.vibrate === "function") {
      navigator.vibrate(style === "heavy" ? 20 : style === "medium" ? 12 : 6);
    }
  } catch {
    // 진동은 보조 피드백이라 실패해도 무시한다.
  }
}
