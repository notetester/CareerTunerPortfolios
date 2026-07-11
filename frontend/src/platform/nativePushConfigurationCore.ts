export type NativePushPlatform = "web" | "ios" | "android";

/** Android는 Firebase 초기화를 확인하고, APNs 발송기가 없는 iOS와 알 수 없는 플랫폼은 닫는다. */
export function shouldRegisterNativePush(
  platform: NativePushPlatform,
  pushConfigured: boolean,
): boolean {
  if (platform === "android") return pushConfigured;
  return false;
}
