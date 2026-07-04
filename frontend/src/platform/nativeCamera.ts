/**
 * 네이티브 카메라 촬영 (@capacitor/camera).
 * 공고·이력서를 폰 카메라로 찍어 바로 업로드하는 흐름에 쓴다.
 * - 네이티브: Camera.getPhoto(촬영/갤러리 선택 프롬프트) → File 변환
 * - 웹/미지원: null 반환 → 호출부가 <input type="file" capture> 폴백을 쓴다
 * 플러그인은 하드 import 하지 않고 런타임 접근(haptics/push 와 동일 패턴).
 */
import { isNativeApp, nativePlugin } from "./capacitor";

interface CapCameraPhoto {
  webPath?: string;
  format?: string;
}
interface CapCamera {
  getPhoto: (opts: {
    quality?: number;
    allowEditing?: boolean;
    resultType: string; // 'uri'
    source?: string;    // 'PROMPT' | 'CAMERA' | 'PHOTOS'
    correctOrientation?: boolean;
  }) => Promise<CapCameraPhoto>;
}

/** 네이티브 카메라 사용 가능 여부 (앱 + 플러그인 로드됨). */
export function isNativeCameraAvailable(): boolean {
  return isNativeApp() && nativePlugin<CapCamera>("Camera") !== undefined;
}

/**
 * 촬영 또는 갤러리에서 사진 1장 → File.
 * 사용자가 취소하면 null (에러 아님). 플러그인 미지원이면 null → input capture 폴백.
 */
export async function capturePhotoFile(): Promise<File | null> {
  const cam = nativePlugin<CapCamera>("Camera");
  if (!isNativeApp() || !cam) return null;
  try {
    const photo = await cam.getPhoto({
      quality: 85,
      allowEditing: false,
      resultType: "uri",
      source: "PROMPT", // 촬영/갤러리 선택 시트
      correctOrientation: true,
    });
    if (!photo?.webPath) return null;
    const blob = await fetch(photo.webPath).then((r) => r.blob());
    const ext = photo.format || "jpeg";
    return new File([blob], `capture-${Date.now()}.${ext}`, {
      type: blob.type || `image/${ext}`,
    });
  } catch {
    return null; // 사용자 취소 포함 — 조용히 무시
  }
}
