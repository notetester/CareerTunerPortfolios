/**
 * 네이티브 카메라 촬영 (@capacitor/camera).
 * 공고·이력서를 폰 카메라로 찍어 바로 업로드하는 흐름에 쓴다.
 * - 네이티브: Camera.getPhoto(촬영/갤러리 선택 프롬프트) → File 변환
 * - 웹/미지원: null 반환 → 호출부가 <input type="file" capture> 폴백을 쓴다
 * 공식 Camera 플러그인을 직접 import해 번들 등록과 네이티브 브리지 호출을 보장한다.
 */
import { Camera, CameraResultType, CameraSource } from "@capacitor/camera";
import { isNativeApp } from "./capacitor";

/** 네이티브 카메라 사용 가능 여부 (앱 + 플러그인 로드됨). */
export function isNativeCameraAvailable(): boolean {
  return isNativeApp();
}

/**
 * 촬영 또는 갤러리에서 사진 1장 → File.
 * 사용자가 취소하면 null (에러 아님). 플러그인 미지원이면 null → input capture 폴백.
 */
export async function capturePhotoFile(): Promise<File | null> {
  if (!isNativeApp()) return null;
  try {
    const photo = await Camera.getPhoto({
      quality: 85,
      allowEditing: false,
      resultType: CameraResultType.Uri,
      source: CameraSource.Prompt, // 촬영/갤러리 선택 시트
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
