import { Bold, Italic, List, ListOrdered, Quote, Link as LinkIcon, Code, ImagePlus } from "lucide-react";
import { useEditor, EditorContent, useEditorState } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Image from "@tiptap/extension-image";
import { pickAndInsertImage, useEditorImagePaste } from "@/app/lib/editorImage";
import { toast } from "@/features/notification/components/toast";

/**
 * FAQ 답변 TipTap 리치텍스트 에디터 (작성 폼 전용).
 * 커뮤니티 방식과 동일한 HTML 출력. sanitize 는 상위(저장 시)에서 커뮤니티 lib/postContent 재사용.
 * onChange 로 원본 HTML + 평문 길이를 부모에 올려준다.
 */
interface Props {
  onChange: (html: string, textLen: number) => void;
}

const EMPTY_STATE = {
  bold: false, italic: false, bulletList: false, orderedList: false,
  blockquote: false, code: false, link: false, isEmpty: true,
};

export default function FaqAnswerEditor({ onChange }: Props) {
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        link: {
          openOnClick: false,
          HTMLAttributes: { rel: "noopener noreferrer nofollow", target: "_blank" },
        },
      }),
      Image.configure({ inline: false, allowBase64: false }),
    ],
    content: "",
    onUpdate: ({ editor }) => onChange(editor.getHTML(), editor.getText().trim().length),
  });

  useEditorImagePaste(editor, "faq", toast.error);

  const es = useEditorState({
    editor,
    selector: ({ editor }) => ({
      bold: !!editor?.isActive("bold"),
      italic: !!editor?.isActive("italic"),
      bulletList: !!editor?.isActive("bulletList"),
      orderedList: !!editor?.isActive("orderedList"),
      blockquote: !!editor?.isActive("blockquote"),
      code: !!editor?.isActive("code"),
      link: !!editor?.isActive("link"),
      isEmpty: editor?.isEmpty ?? true,
    }),
  }) ?? EMPTY_STATE;

  const setLink = () => {
    if (!editor) return;
    const prev = (editor.getAttributes("link").href as string) ?? "";
    const url = window.prompt("링크 URL을 입력하세요", prev);
    if (url === null) return;
    if (url.trim() === "") {
      editor.chain().focus().extendMarkRange("link").unsetLink().run();
      return;
    }
    editor.chain().focus().extendMarkRange("link").setLink({ href: url.trim() }).run();
  };

  return (
    <div className="fc-editor">
      <div className="fc-toolbar">
        <button type="button" className={"fc-tool" + (es.bold ? " on" : "")} title="굵게" aria-label="굵게"
          onClick={() => editor?.chain().focus().toggleBold().run()}><Bold /></button>
        <button type="button" className={"fc-tool" + (es.italic ? " on" : "")} title="기울임" aria-label="기울임"
          onClick={() => editor?.chain().focus().toggleItalic().run()}><Italic /></button>
        <span className="fc-tooldiv" />
        <button type="button" className={"fc-tool" + (es.bulletList ? " on" : "")} title="글머리 목록" aria-label="글머리 목록"
          onClick={() => editor?.chain().focus().toggleBulletList().run()}><List /></button>
        <button type="button" className={"fc-tool" + (es.orderedList ? " on" : "")} title="번호 목록" aria-label="번호 목록"
          onClick={() => editor?.chain().focus().toggleOrderedList().run()}><ListOrdered /></button>
        <button type="button" className={"fc-tool" + (es.blockquote ? " on" : "")} title="인용" aria-label="인용"
          onClick={() => editor?.chain().focus().toggleBlockquote().run()}><Quote /></button>
        <span className="fc-tooldiv" />
        <button type="button" className={"fc-tool" + (es.link ? " on" : "")} title="링크" aria-label="링크"
          onClick={setLink}><LinkIcon /></button>
        <button type="button" className="fc-tool" title="이미지" aria-label="이미지"
          onClick={() => editor && pickAndInsertImage(editor, "faq", toast.error)}><ImagePlus /></button>
        <button type="button" className={"fc-tool" + (es.code ? " on" : "")} title="코드" aria-label="코드"
          onClick={() => editor?.chain().focus().toggleCode().run()}><Code /></button>
      </div>
      <div className="fc-editor__body">
        {es.isEmpty && (
          <div className="fc-editor__ph" aria-hidden="true">
            {"답변을 입력하세요.\n\n좋은 답변 = 결론 1문장 + 조건·예외 + 안 될 때 다음 행동(문의 링크)."}
          </div>
        )}
        <EditorContent editor={editor} />
      </div>
    </div>
  );
}
