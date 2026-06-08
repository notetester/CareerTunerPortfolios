import { useState } from "react";
import { Input } from "@/app/components/ui/input";
import { Switch } from "@/app/components/ui/switch";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/app/components/ui/select";
import {
  ShieldAlert, Bold, Italic, Heading, List, ListOrdered,
  Code, Link, Image, Plus, Trash2, Star, Send, Check, X,
  ClipboardList,
} from "lucide-react";
import { CATEGORIES } from "../types/community";

interface PostEditorFormProps {
  onCancel: () => void;
  onSubmit?: () => void;
}

const INTERVIEW_TYPES = ["전화 면접", "화상 면접", "대면 면접", "코딩테스트", "과제 전형"];
const RESULTS = ["합격", "불합격", "대기중", "비공개"];

function StarPicker({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  const [hover, setHover] = useState(0);
  const active = hover || value;
  return (
    <div className="ct-starpick" onMouseLeave={() => setHover(0)}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
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
    if (t && !tags.includes(t) && tags.length < 8) onChange([...tags, t]);
    setDraft("");
  };
  return (
    <div
      className="ct-tags"
      onClick={(e) => (e.currentTarget.querySelector("input") as HTMLInputElement)?.focus()}
    >
      {tags.map((t) => (
        <span key={t} className="ct-tag">
          #{t}
          <button type="button" onClick={() => onChange(tags.filter((x) => x !== t))}>
            <X />
          </button>
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
        placeholder={tags.length ? "" : "태그 입력 후 Enter (최대 8개)"}
      />
    </div>
  );
}

export function PostEditorForm({ onCancel, onSubmit }: PostEditorFormProps) {
  const cats = CATEGORIES.filter((c) => c.value !== "all");
  const [cat, setCat] = useState("interview");
  const [title, setTitle] = useState("");
  const [bodyText, setBodyText] = useState("");
  const [tags, setTags] = useState<string[]>(["신입공채", "코딩테스트"]);
  const [anon, setAnon] = useState(true);
  const [company, setCompany] = useState("");
  const [role, setRole] = useState("");
  const [itype, setIType] = useState("");
  const [difficulty, setDifficulty] = useState(0);
  const [date, setDate] = useState("");
  const [result, setResult] = useState("");
  const [questions, setQuestions] = useState([""]);

  const isInterview = cat === "interview";
  const canPost = title.trim() && bodyText.trim();

  const setQ = (i: number, v: string) => setQuestions((q) => q.map((x, j) => (j === i ? v : x)));
  const addQ = () => setQuestions((q) => [...q, ""]);
  const rmQ = (i: number) => setQuestions((q) => (q.length > 1 ? q.filter((_, j) => j !== i) : q));

  return (
    <div className="ct-page ct-compose">
      {/* Page head */}
      <div className="ct-pagehead">
        <h1>글쓰기</h1>
        <p>경험을 나누면 누군가의 합격이 빨라져요.</p>
      </div>

      {/* Warning */}
      <div className="ct-compose__warn">
        <ShieldAlert />
        <div>
          <b>기밀정보·실명 작성에 주의해주세요</b>
          <p>
            회사 내부 자료, 미공개 채용 정보, 본인·타인의 실명·연락처 등 개인정보는
            작성을 삼가주세요. 게시물은 커뮤니티 가이드라인에 따라 관리됩니다.
          </p>
        </div>
      </div>

      {/* Category chips */}
      <div className="ct-field">
        <div className="ct-field__label">
          카테고리 <span className="ct-field__req">*</span>
        </div>
        <div className="ct-catpick">
          {cats.map((c) => {
            const on = cat === c.value;
            return (
              <button
                key={c.value}
                type="button"
                className={`ct-catchip ${on ? "is-on" : ""}`}
                style={
                  on
                    ? { background: `var(--${c.colorClass}-bg)`, color: `var(--${c.colorClass}-fg)` }
                    : undefined
                }
                onClick={() => setCat(c.value)}
              >
                {c.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Title */}
      <div className="ct-field">
        <div className="ct-field__label">
          제목 <span className="ct-field__req">*</span>
        </div>
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="제목을 입력하세요"
          maxLength={60}
        />
      </div>

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
              <label className="ct-field__label">회사명</label>
              <Input value={company} onChange={(e) => setCompany(e.target.value)} placeholder="예) 쿠팡" />
            </div>
            <div>
              <label className="ct-field__label">직무</label>
              <Input value={role} onChange={(e) => setRole(e.target.value)} placeholder="예) 백엔드 개발자" />
            </div>
            <div>
              <label className="ct-field__label">면접 유형</label>
              <Select value={itype} onValueChange={setIType}>
                <SelectTrigger><SelectValue placeholder="선택하세요" /></SelectTrigger>
                <SelectContent>
                  {INTERVIEW_TYPES.map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div>
              <label className="ct-field__label">결과</label>
              <Select value={result} onValueChange={setResult}>
                <SelectTrigger><SelectValue placeholder="선택하세요" /></SelectTrigger>
                <SelectContent>
                  {RESULTS.map((r) => <SelectItem key={r} value={r}>{r}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div>
              <label className="ct-field__label">면접일</label>
              <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </div>
            <div>
              <label className="ct-field__label">체감 난이도</label>
              <StarPicker value={difficulty} onChange={setDifficulty} />
            </div>
            <div className="ct-iblock__full">
              <label className="ct-field__label">면접 질문 목록</label>
              <div className="ct-qlist">
                {questions.map((q, i) => (
                  <div key={i} className="ct-qrow">
                    <span className="ct-qrow__n">{i + 1}</span>
                    <div style={{ flex: 1 }}>
                      <Input
                        value={q}
                        onChange={(e) => setQ(i, e.target.value)}
                        placeholder="받았던 질문을 적어주세요"
                      />
                    </div>
                    <button
                      type="button"
                      className="ct-qrow__rm"
                      onClick={() => rmQ(i)}
                      disabled={questions.length === 1}
                    >
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

      {/* Editor */}
      <div className="ct-field">
        <div className="ct-field__label">
          본문 <span className="ct-field__req">*</span>
        </div>
        <div className="ct-editor">
          <div className="ct-editor__bar">
            <button type="button"><Bold /></button>
            <button type="button"><Italic /></button>
            <span className="sep" />
            <button type="button"><Heading /></button>
            <button type="button"><List /></button>
            <button type="button"><ListOrdered /></button>
            <span className="sep" />
            <button type="button"><Code /></button>
            <button type="button"><Link /></button>
            <button type="button"><Image /></button>
          </div>
          <textarea
            value={bodyText}
            onChange={(e) => setBodyText(e.target.value)}
            placeholder="면접 과정, 분위기, 받은 질문과 팁을 자유롭게 적어주세요."
          />
        </div>
        <div className="ct-field__hint">
          마크다운(**굵게**, ## 제목, - 목록, ``` 코드)을 지원해요.
        </div>
      </div>

      {/* Tags */}
      <div className="ct-field">
        <div className="ct-field__label">태그</div>
        <TagInput tags={tags} onChange={setTags} />
        <div className="ct-field__hint">
          검색과 추천에 사용돼요. 직무·전형·회사명 등을 추가해보세요.
        </div>
      </div>

      {/* Anonymous */}
      <div className="ct-field">
        <div className="ct-switchrow">
          <div>
            <div className="ct-switchrow__t">익명으로 작성</div>
            <div className="ct-switchrow__d">
              이름 대신 직무 라벨만 표시됩니다. 끄면 프로필이 공개돼요.
            </div>
          </div>
          <Switch checked={anon} onCheckedChange={setAnon} />
        </div>
      </div>

      {/* Sticky footer */}
      <div className="ct-compose__foot">
        <span className="ct-compose__draft">
          <Check />임시저장됨 · 방금
        </span>
        <div className="right">
          <button type="button" className="ct-act" onClick={onCancel}>
            취소
          </button>
          <button
            className="ct-btn-brand"
            disabled={!canPost}
            onClick={onSubmit}
          >
            게시하기 <Send />
          </button>
        </div>
      </div>
    </div>
  );
}
