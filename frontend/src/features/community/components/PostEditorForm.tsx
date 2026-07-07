import { useState } from "react";
import {
  ArrowLeft, Bold, Italic, List, ListOrdered, Quote,
  Link as LinkIcon, Code, Plus, Trash2, Star, Hash, X,
  ClipboardList,
} from "lucide-react";
import { useEditor, EditorContent, useEditorState } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { CATEGORIES, type CommunityCategory } from "../types/community";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { toast } from "@/features/notification/components/toast";
import { sanitizePostHtml, isHtmlContent, plainToHtml } from "@/app/lib/postContent";

// 백엔드 CreatePostRequest/UpdatePostRequest 의 @Size(max=20000) 와 정합
const MAX_HTML = 20000;

export interface PostEditData {
  id: number;
  category: string;
  title: string;
  content: string;
  tags: string[];
  anonymous: boolean;
  interviewReview?: {
    companyName: string;
    jobRole: string;
    interviewType?: string;
    difficulty?: number | null;
    interviewDate?: string;
    resultStatus?: string;
    questions?: string[];
  };
}

interface PostEditorFormProps {
  onCancel: () => void;
  onSubmit?: () => void;
  editData?: PostEditData;
}

const RESULT_MAP: Record<string, string> = {
  "합격": "PASSED", "불합격": "FAILED", "대기중": "PENDING", "비공개": "UNKNOWN",
};

const INTERVIEW_TYPES = ["전화 면접", "화상 면접", "대면 면접", "코딩테스트", "과제 전형"];
const RESULTS = ["합격", "불합격", "대기중", "비공개"];

function StarPicker({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  const [hover, setHover] = useState(0);
  const active = hover || value;
  return (
    <div className="ct-starpick" onMouseLeave={() => setHover(0)}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n} type="button"
          className={n <= active ? "on" : ""}
          onMouseEnter={() => setHover(n)}
          onClick={() => onChange(n)}
        >
          <Star />
        </button>
      ))}
      <span className="ct-starpick__n">{value ? `${value} / 5` : "선택"}</span>
    </div>
  );
}

function TagInput({ tags, onChange }: { tags: string[]; onChange: (t: string[]) => void }) {
  const [draft, setDraft] = useState("");
  const add = () => {
    const t = draft.trim().replace(/^#/, "");
    if (t && !tags.includes(t) && tags.length < 5) onChange([...tags, t]);
    setDraft("");
  };
  return (
    <div className="wv-tags">
      <Hash />
      {tags.map((t) => (
        <span key={t} className="wv-tag">
          {t}
          <button type="button" onClick={() => onChange(tags.filter((x) => x !== t))}><X /></button>
        </span>
      ))}
      <input
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === ",") { e.preventDefault(); add(); }
          else if (e.key === "Backspace" && !draft && tags.length) onChange(tags.slice(0, -1));
        }}
        onBlur={add}
        placeholder={tags.length ? "" : "태그 입력 (쉼표로 구분, 최대 5개)"}
      />
    </div>
  );
}

const REVERSE_RESULT_MAP: Record<string, string> = {
  "PASSED": "합격", "FAILED": "불합격", "PENDING": "대기중", "UNKNOWN": "비공개",
};

export function PostEditorForm({ onCancel, onSubmit, editData }: PostEditorFormProps) {
  const { createPost, updatePost } = useCommunityStore();
  const isEdit = !!editData;
  const cats = CATEGORIES.filter((c) => c.value !== "all" && c.value !== "recruit");
  const [cat, setCat] = useState(editData?.category ?? "interview");
  const [submitting, setSubmitting] = useState(false);
  const [title, setTitle] = useState(editData?.title ?? "");
  const [tags, setTags] = useState<string[]>(editData?.tags ?? []);
  const [anon, setAnon] = useState(editData?.anonymous ?? true);
  const [company, setCompany] = useState(editData?.interviewReview?.companyName ?? "");
  const [role, setRole] = useState(editData?.interviewReview?.jobRole ?? "");
  const [itype, setIType] = useState(editData?.interviewReview?.interviewType ?? "");
  const [difficulty, setDifficulty] = useState(editData?.interviewReview?.difficulty ?? 0);
  const [date, setDate] = useState(editData?.interviewReview?.interviewDate ?? "");
  const [result, setResult] = useState(
    editData?.interviewReview?.resultStatus
      ? (REVERSE_RESULT_MAP[editData.interviewReview.resultStatus] ?? "")
      : "",
  );
  const [questions, setQuestions] = useState(
    editData?.interviewReview?.questions?.length
      ? editData.interviewReview.questions
      : [""],
  );

  // 기존 글 편집: HTML이면 그대로, 평문(기존 118건)이면 안전 HTML로 변환해 로드
  const initialContent = editData?.content
    ? sanitizePostHtml(isHtmlContent(editData.content) ? editData.content : plainToHtml(editData.content))
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
    content: initialContent,
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
      textLen: editor?.getText().trim().length ?? 0,
    }),
  }) ?? {
    bold: false, italic: false, bulletList: false, orderedList: false,
    blockquote: false, code: false, link: false, isEmpty: true, textLen: 0,
  };

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

  const isInterview = cat === "interview";
  const canPost = title.trim().length > 1 && es.textLen > 9 && !submitting;

  const handlePost = async () => {
    if (!canPost || !editor) return;
    const html = sanitizePostHtml(editor.getHTML());
    if (html.length > MAX_HTML) {
      toast.error("본문이 너무 깁니다. 내용을 조금 줄여주세요.");
      return;
    }
    setSubmitting(true);
    const catInfo = cats.find((c) => c.value === cat);
    const interviewPayload = isInterview ? {
      companyName: company,
      jobRole: role,
      interviewType: itype || undefined,
      difficulty: difficulty || null,
      interviewDate: date || undefined,
      resultStatus: RESULT_MAP[result] || undefined,
      questions: questions.filter((q) => q.trim()),
    } : undefined;
    try {
      if (isEdit) {
        await updatePost(editData.id, {
          title,
          content: html,
          tags,
          anonymous: anon,
          interviewReview: interviewPayload,
        });
        toast.success("글이 수정되었습니다.");
      } else {
        await createPost({
          category: (catInfo?.slug ?? "free") as CommunityCategory,
          title,
          content: html,
          tags,
          anonymous: anon,
          interviewReview: interviewPayload,
        });
        toast.success("글이 등록되었습니다.");
      }
      onSubmit?.();
    } catch {
      toast.error(isEdit ? "글 수정에 실패했습니다. 다시 시도해주세요." : "글 등록에 실패했습니다. 다시 시도해주세요.");
      setSubmitting(false);
    }
  };

  const setQ = (i: number, v: string) => setQuestions((q) => q.map((x, j) => (j === i ? v : x)));
  const addQ = () => setQuestions((q) => [...q, ""]);
  const rmQ = (i: number) => setQuestions((q) => (q.length > 1 ? q.filter((_, j) => j !== i) : q));

  return (
    <>
      <div className="wv-wrap">
        <a className="wv-back" onClick={onCancel} style={{ cursor: "pointer" }}>
          <ArrowLeft />커뮤니티로 돌아가기
        </a>
        <div className="uv-phead"><div><h1>{isEdit ? "글 수정" : "글쓰기"}</h1></div></div>

        {/* Category chips */}
        <div className="wv-cats">
          {cats.map((c) => (
            <button
              key={c.value} type="button"
              className={"wv-cat" + (cat === c.value ? " on" : "")}
              onClick={() => !isEdit && setCat(c.value)}
              style={isEdit && cat !== c.value ? { opacity: 0.4, cursor: "default" } : undefined}
            >
              {c.label}
            </button>
          ))}
        </div>

        {/* Title */}
        <input
          className="wv-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="제목을 입력하세요"
          maxLength={80}
        />

        {/* Interview block */}
        {isInterview && (
          <div className="ct-iblock">
            <div className="ct-iblock__head">
              <ClipboardList />
              <h4>면접 정보</h4>
              <span>면접후기 작성 시 함께 보여집니다</span>
            </div>
            <div className="ct-iblock__body">
              <div>
                <label className="wv-label">회사명</label>
                <input className="wv-input" value={company} onChange={(e) => setCompany(e.target.value)} placeholder="예) 쿠팡" />
              </div>
              <div>
                <label className="wv-label">직무</label>
                <input className="wv-input" value={role} onChange={(e) => setRole(e.target.value)} placeholder="예) 백엔드 개발자" />
              </div>
              <div>
                <label className="wv-label">면접 유형</label>
                <select className="wv-input" value={itype} onChange={(e) => setIType(e.target.value)}>
                  <option value="">선택하세요</option>
                  {INTERVIEW_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label className="wv-label">결과</label>
                <select className="wv-input" value={result} onChange={(e) => setResult(e.target.value)}>
                  <option value="">선택하세요</option>
                  {RESULTS.map((r) => <option key={r} value={r}>{r}</option>)}
                </select>
              </div>
              <div>
                <label className="wv-label">면접일</label>
                <input className="wv-input" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
              </div>
              <div>
                <label className="wv-label">체감 난이도</label>
                <StarPicker value={difficulty} onChange={setDifficulty} />
              </div>
              <div className="ct-iblock__full">
                <label className="wv-label">면접 질문 목록</label>
                <div className="ct-qlist">
                  {questions.map((q, i) => (
                    <div key={i} className="ct-qrow">
                      <span className="ct-qrow__n">{i + 1}</span>
                      <div style={{ flex: 1 }}>
                        <input className="wv-input" value={q} onChange={(e) => setQ(i, e.target.value)} placeholder="받았던 질문을 적어주세요" />
                      </div>
                      <button type="button" className="ct-qrow__rm" onClick={() => rmQ(i)} disabled={questions.length === 1}>
                        <Trash2 />
                      </button>
                    </div>
                  ))}
                </div>
                <button type="button" className="ct-qadd" onClick={addQ}>
                  <Plus /> 질문 추가
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Toolbar (TipTap) */}
        <div className="wv-toolbar">
          <button type="button" className={"wv-tool" + (es.bold ? " on" : "")} title="굵게" aria-label="굵게"
            onClick={() => editor?.chain().focus().toggleBold().run()}><Bold /></button>
          <button type="button" className={"wv-tool" + (es.italic ? " on" : "")} title="기울임" aria-label="기울임"
            onClick={() => editor?.chain().focus().toggleItalic().run()}><Italic /></button>
          <span className="wv-tooldiv" />
          <button type="button" className={"wv-tool" + (es.bulletList ? " on" : "")} title="글머리 목록" aria-label="글머리 목록"
            onClick={() => editor?.chain().focus().toggleBulletList().run()}><List /></button>
          <button type="button" className={"wv-tool" + (es.orderedList ? " on" : "")} title="번호 목록" aria-label="번호 목록"
            onClick={() => editor?.chain().focus().toggleOrderedList().run()}><ListOrdered /></button>
          <button type="button" className={"wv-tool" + (es.blockquote ? " on" : "")} title="인용" aria-label="인용"
            onClick={() => editor?.chain().focus().toggleBlockquote().run()}><Quote /></button>
          <span className="wv-tooldiv" />
          <button type="button" className={"wv-tool" + (es.link ? " on" : "")} title="링크" aria-label="링크"
            onClick={setLink}><LinkIcon /></button>
          <button type="button" className={"wv-tool" + (es.code ? " on" : "")} title="코드" aria-label="코드"
            onClick={() => editor?.chain().focus().toggleCode().run()}><Code /></button>
        </div>

        {/* Body (TipTap) */}
        <div className="wv-body">
          {es.isEmpty && (
            <div className="wv-body__ph" aria-hidden="true">
              {"내용을 입력하세요.\n\n면접 후기라면 — 기억나는 질문, 분위기, 준비하면서 도움이 됐던 것들을 적어주시면 다른 분들에게 큰 도움이 됩니다."}
            </div>
          )}
          <EditorContent editor={editor} />
        </div>

        {/* Tags */}
        <TagInput tags={tags} onChange={setTags} />

        {/* Note */}
        <div className="av-note" style={{ margin: "16px 0 0" }}>
          회사·개인을 특정한 비방, 허위 정보, 개인정보가 포함된 글은 <b>예고 없이 숨김 처리</b>될 수 있어요.
        </div>
      </div>

      {/* Sticky footer */}
      <div className="wv-foot">
        <div className="wv-foot__in">
          <div className="wv-foot__r">
            <label className="wv-anon">
              <input type="checkbox" checked={anon} onChange={(e) => setAnon(e.target.checked)} />
              익명으로 작성
            </label>
            <button type="button" className="av-btn" onClick={onCancel}>취소</button>
            <button
              className="av-btn av-btn--ink"
              disabled={!canPost}
              style={!canPost ? { opacity: 0.45, cursor: "default" } : undefined}
              onClick={handlePost}
            >
              {isEdit ? "수정" : "등록"}
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
