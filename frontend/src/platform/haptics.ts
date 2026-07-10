/**
 * 햅틱(진동) 피드백 어댑터.
 * 네이티브: Capacitor Haptics 플러그인(있으면) → 웹: navigator.vibrate → 미지원: no-op.
 */
import { Haptics, ImpactStyle } from "@capacitor/haptics";
import { isNativeApp } from "./capacitor";

type HapticStyle = "light" | "medium" | "heavy";

export function haptic(style: HapticStyle = "light"): void {
  try {
    if (isNativeApp()) {
      const nativeStyle = style === "heavy" ? ImpactStyle.Heavy : style === "medium" ? ImpactStyle.Medium : ImpactStyle.Light;
      void Haptics.impact({ style: nativeStyle }).catch(() => {});
      return;
    }
    if (typeof navigator !== "undefined" && typeof navigator.vibrate === "function") {
      navigator.vibrate(style === "heavy" ? 20 : style === "medium" ? 12 : 6);
    }
  } catch {
    // 진동은 보조 피드백이라 실패해도 무시한다.
  }
}
