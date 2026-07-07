import { Bold, Italic, List, ListOrdered, Quote, Link as LinkIcon, Code } from "lucide-react";
import { useEditor, EditorContent, useEditorState } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { isHtmlContent, plainToHtml, sanitizePostHtml } from "@/app/lib/postContent";

/**
 * 공지 본문 TipTap 리치텍스트 에디터 (작성+수정 겸용).
 * 커뮤니티/FAQ와 동일한 HTML 출력. 공통 lib/postContent 로 sanitize/감지 재사용.
 * 기존 공지(마크다운/평문)를 수정 폼에 로드할 땐 plainToHtml 로 원문(마크다운 문법 포함)을
 * 그대로 편집 가능한 텍스트로 넣는다. onCreate/onUpdate 로 HTML+평문길이를 부모에 전달.
 */
interface Props {
  initialContent: string;
  onChange: (html: string, textLen: number) => void;
}

const EMPTY_STATE = {
  bold: false, italic: false, bulletList: false, orderedList: false,
  blockquote: false, code: false, link: false, isEmpty: true,
};

export default function NoticeBodyEditor({ initialContent, onChange }: Props) {
  // 기존 공지: HTML이면 그대로, 아니면(마크다운/평문) plainToHtml 로 원문 보존 로드
  const initial = initialContent
    ? sanitizePostHtml(isHtmlContent(initialContent) ? initialContent : plainToHtml(initialContent))
    : "";

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        link: {
          openOnClick: false,
          HTMLAttributes: { rel: "noopener noreferrer nofollow", target: "_blank" },
        },
      }),
    ],
    content: initial,
    onCreate: ({ editor }) => onChange(editor.getHTML(), editor.getText().trim().length),
    onUpdate: ({ editor }) => onChange(editor.getHTML(), editor.getText().trim().length),
  });

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
    <div className="nc-editor">
      <div className="nc-toolbar">
        <button type="button" className={"nc-tool" + (es.bold ? " on" : "")} title="굵게" aria-label="굵게"
          onClick={() => editor?.chain().focus().toggleBold().run()}><Bold /></button>
        <button type="button" className={"nc-tool" + (es.italic ? " on" : "")} title="기울임" aria-label="기울임"
          onClick={() => editor?.chain().focus().toggleItalic().run()}><Italic /></button>
        <span className="nc-tooldiv" />
        <button type="button" className={"nc-tool" + (es.bulletList ? " on" : "")} title="글머리 목록" aria-label="글머리 목록"
          onClick={() => editor?.chain().focus().toggleBulletList().run()}><List /></button>
        <button type="button" className={"nc-tool" + (es.orderedList ? " on" : "")} title="번호 목록" aria-label="번호 목록"
          onClick={() => editor?.chain().focus().toggleOrderedList().run()}><ListOrdered /></button>
        <button type="button" className={"nc-tool" + (es.blockquote ? " on" : "")} title="인용" aria-label="인용"
          onClick={() => editor?.chain().focus().toggleBlockquote().run()}><Quote /></button>
        <span className="nc-tooldiv" />
        <button type="button" className={"nc-tool" + (es.link ? " on" : "")} title="링크" aria-label="링크"
          onClick={setLink}><LinkIcon /></button>
        <button type="button" className={"nc-tool" + (es.code ? " on" : "")} title="코드" aria-label="코드"
          onClick={() => editor?.chain().focus().toggleCode().run()}><Code /></button>
      </div>
      <div className="nc-editor__body">
        {es.isEmpty && (
          <div className="nc-editor__ph" aria-hidden="true">
            {"공지 내용을 입력하세요.\n\n점검 공지라면 — 일시, 영향 범위(접속 불가/일부 기능), 사유를 순서대로 적어주세요."}
          </div>
        )}
        <EditorContent editor={editor} />
      </div>
    </div>
  );
}
