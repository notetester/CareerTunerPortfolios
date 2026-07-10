/**
 * 리치텍스트 에디터(커뮤니티 글 / 공지 본문 / FAQ 답변) 공통 이미지 첨부 유틸.
 * 파일 선택·붙여넣기·드래그로 고른 이미지를 백엔드(POST /api/community/images)에 올리고,
 * 반환된 public URL 을 TipTap 에디터의 <img> 로 삽입한다. 저장 스택은 B가 도입한 Cloudinary 를 재사용한다.
 */
import { useEffect } from "react";
import type { Editor } from "@tiptap/react";
import { api } from "@/app/lib/api";

/** 이미지 저장 폴더 분리 키. 백엔드 CommunityImageService.SCOPES 와 정합. */
export type ImageScope = "community" | "notice" | "faq";

const IMAGE_TYPES = ["image/png", "image/jpeg", "image/webp", "image/gif"];
const MAX_BYTES = 5 * 1024 * 1024; // 백엔드 CommunityImageProperties.maxFileSizeBytes(기본 5MB)와 정합

/** 이미지 1장을 올리고 본문 삽입용 public URL 을 돌려준다. */
export async function uploadEditorImage(file: File, scope: ImageScope): Promise<string> {
  const form = new FormData();
  form.append("file", file);
  form.append("scope", scope);
  const { url } = await api<{ url: string }>("/community/images", { method: "POST", body: form });
  return url;
}

/** 파일 검증 → 업로드 → 에디터 커서 위치에 <img> 삽입. 실패 사유는 onError 로 전달. */
export async function insertImageFile(
  editor: Editor,
  file: File,
  scope: ImageScope,
  onError?: (message: string) => void,
): Promise<void> {
  if (!IMAGE_TYPES.includes(file.type)) {
    onError?.("PNG, JPG, WEBP, GIF 이미지만 올릴 수 있습니다.");
    return;
  }
  if (file.size > MAX_BYTES) {
    onError?.("이미지는 5MB 이하만 올릴 수 있습니다.");
    return;
  }
  try {
    const url = await uploadEditorImage(file, scope);
    editor.chain().focus().setImage({ src: url }).run();
  } catch {
    onError?.("이미지 업로드에 실패했습니다. 잠시 후 다시 시도해 주세요.");
  }
}

/** 파일 선택창을 열어 고른 이미지를 삽입한다(툴바 이미지 버튼용). */
export function pickAndInsertImage(
  editor: Editor,
  scope: ImageScope,
  onError?: (message: string) => void,
): void {
  const input = document.createElement("input");
  input.type = "file";
  input.accept = IMAGE_TYPES.join(",");
  input.onchange = () => {
    const file = input.files?.[0];
    if (file) void insertImageFile(editor, file, scope, onError);
  };
  input.click();
}

/**
 * 에디터에 이미지 붙여넣기/드롭 업로드 핸들러를 붙인다(스크린샷 붙여넣기·파일 드래그).
 * 이미지 파일이 감지되면 기본 삽입(base64 인라인)을 막고 업로드 경로로 태운다.
 */
export function useEditorImagePaste(
  editor: Editor | null,
  scope: ImageScope,
  onError?: (message: string) => void,
): void {
  useEffect(() => {
    if (!editor) return;
    const dom = editor.view.dom;
    const imagesFrom = (list?: FileList | null) =>
      Array.from(list ?? []).filter((f) => f.type.startsWith("image/"));

    const onPaste = (e: ClipboardEvent) => {
      const files = imagesFrom(e.clipboardData?.files);
      if (files.length === 0) return;
      e.preventDefault();
      files.forEach((f) => void insertImageFile(editor, f, scope, onError));
    };
    const onDrop = (e: DragEvent) => {
      const files = imagesFrom(e.dataTransfer?.files);
      if (files.length === 0) return;
      e.preventDefault();
      files.forEach((f) => void insertImageFile(editor, f, scope, onError));
    };

    dom.addEventListener("paste", onPaste);
    dom.addEventListener("drop", onDrop);
    return () => {
      dom.removeEventListener("paste", onPaste);
      dom.removeEventListener("drop", onDrop);
    };
  }, [editor, scope, onError]);
}
