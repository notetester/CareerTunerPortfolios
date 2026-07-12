import { registerPlugin } from "@capacitor/core";
import { isNativeApp, nativePlugin, platformName } from "./capacitor";
import { shouldRegisterNativePush } from "./nativePushConfigurationCore";

interface NativeConfigurationPlugin {
  getCapabilities: () => Promise<{ pushConfigured?: boolean }>;
}

const registeredNativeConfiguration = registerPlugin<NativeConfigurationPlugin>("NativeConfiguration");

/**
 * Capacitor PushNotifications.register()을 호출해도 안전한 네이티브 구성인지 확인한다.
 * Android의 google-services.json 누락은 플러그인 설치 여부만으로 판별할 수 없으므로
 * 네이티브가 실제 기본 FirebaseApp 초기화 상태를 보고한다. iOS는 현재 서버에 APNs
 * 발송기가 없으므로 원시 APNs 토큰을 FCM 토큰으로 오등록하지 않고 fail-closed한다.
 */
export async function isNativePushRegistrationConfigured(): Promise<boolean> {
  if (!isNativeApp()) return false;
  const platform = platformName();
  if (platform !== "android") return false;
  if (shouldRegisterNativePush(platform, false)) return true;

  const plugin = nativePlugin<NativeConfigurationPlugin>("NativeConfiguration")
    ?? registeredNativeConfiguration;
  try {
    const capabilities = await plugin.getCapabilities();
    return shouldRegisterNativePush(platform, capabilities.pushConfigured === true);
  } catch {
    return false;
  }
}
