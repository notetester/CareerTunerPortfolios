import { useState, useRef, useEffect, type ChangeEvent } from "react";
import {
  CircleHelp, Plus, PenLine, Save, Trash2, X,
  ImagePlus, Youtube, Image as ImageIcon,
} from "lucide-react";
import { Input } from "@/app/components/ui/input";
import { Switch } from "@/app/components/ui/switch";
import { Button } from "@/app/components/ui/button";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/app/components/ui/select";
import AdminShell from "../../../components/AdminShell";
import {
  FAQ_CATEGORIES, CAT_COLOR,
  type Faq, type FaqCategory,
} from "../data/faqData";
import * as adminFaqApi from "../api/adminFaqApi";
import "./admin-faq.css";

const YT_RE = /(?:youtu\.be\/|v=|embed\/|shorts\/)([\w-]{11})/;

function parseYtId(url: string): string | null {
  const m = url.match(YT_RE);
  return m ? m[1] : null;
}

function readFileAsDataURL(file: File): Promise<string> {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.readAsDataURL(file);
  });
}

function Toast({ msg, tone }: { msg: string; tone: string }) {
  return <div className={`faq-toast faq-toast--${tone}`}>{msg}</div>;
}

export default function AdminFaq() {
  const [items, setItems] = useState<Faq[]>([]);
  const [catFilter, setCatFilter] = useState<string>("전체");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [isNew, setIsNew] = useState(false);

  /* editor */
  const [eCat, setECat] = useState<FaqCategory>("일반");
  const [eQ, setEQ] = useState("");
  const [eA, setEA] = useState("");
  const [eOn, setEOn] = useState(true);
  const [eImages, setEImages] = useState<string[]>([]);
  const [eYt, setEYt] = useState("");
  const [ytInput, setYtInput] = useState("");

  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);
  const imgInputRef = useRef<HTMLInputElement>(null);

  /* 초기 목록 로드 */
  useEffect(() => {
    adminFaqApi.getFaqs().then((list) => {
      setItems(list);
      if (list.length > 0) loadEditor(list[0]);
    }).catch(() => flash("FAQ 목록을 불러오지 못했습니다.", "red"));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* derived */
  const filtered = catFilter === "전체" ? items : items.filter((f) => f.cat === catFilter);
  const selectedItem = items.find((f) => f.id === selectedId) ?? null;
  const editorLabel = isNew ? "새 FAQ 작성" : "FAQ 편집";
  const editorStatus = isNew ? null : selectedItem?.on ?? null;

  const catCounts: Record<string, number> = { "전체": items.length };
  for (const c of FAQ_CATEGORIES) catCounts[c] = items.filter((f) => f.cat === c).length;

  /* load */
  const loadEditor = (f: Faq) => {
    setSelectedId(f.id);
    setIsNew(false);
    setECat(f.cat);
    setEQ(f.q);
    setEA(f.a);
    setEOn(f.on);
    setEImages(f.images);
    setEYt(f.yt);
    setYtInput(f.yt);
  };

  const handleNew = () => {
    setIsNew(true);
    setSelectedId(null);
    setECat("일반");
    setEQ("");
    setEA("");
    setEOn(true);
    setEImages([]);
    setEYt("");
    setYtInput("");
  };

  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  /* save */
  const handleSave = async () => {
    if (!eQ.trim()) { flash("질문을 입력해주세요", "red"); return; }

    try {
      if (isNew) {
        const created = await adminFaqApi.createFaq({ cat: eCat, q: eQ, a: eA, on: eOn });
        const newList = await adminFaqApi.getFaqs();
        setItems(newList);
        setSelectedId(created.id);
        setIsNew(false);
      } else if (selectedId) {
        await adminFaqApi.updateFaq(selectedId, { cat: eCat, q: eQ, a: eA, on: eOn });
        const newList = await adminFaqApi.getFaqs();
        setItems(newList);
      }
      flash("저장했어요", "green");
    } catch {
      flash("저장에 실패했습니다.", "red");
    }
  };

  /* delete */
  const handleDelete = async () => {
    if (isNew || !selectedId) return;
    try {
      await adminFaqApi.deleteFaq(selectedId);
      const newList = await adminFaqApi.getFaqs();
      setItems(newList);
      if (newList.length > 0) loadEditor(newList[0]);
      else handleNew();
      flash("삭제했어요", "red");
    } catch {
      flash("삭제에 실패했습니다.", "red");
    }
  };

  /* images */
  const addImages = async (files: FileList) => {
    const urls: string[] = [];
    for (const f of Array.from(files)) {
      if (f.type.startsWith("image/")) urls.push(await readFileAsDataURL(f));
    }
    setEImages((prev) => [...prev, ...urls]);
  };

  const onImgInput = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) addImages(e.target.files);
    e.target.value = "";
  };

  /* youtube */
  const handleYtApply = () => {
    const id = parseYtId(ytInput);
    if (id) {
      setEYt(`https://www.youtube.com/embed/${id}`);
    }
  };

  return (
    <AdminShell
      active="faq"
      breadcrumb="FAQ 관리"
      title="FAQ 관리"
      icon={CircleHelp}
      desc="자주 묻는 질문을 작성하고 게시 상태를 관리합니다."
      actions={
        <Button
          className="bg-gradient-to-r from-blue-600 to-indigo-600 text-white"
          size="sm"
          onClick={handleNew}
        >
          <Plus /> 새 FAQ
        </Button>
      }
    >
      <div className="faq-body">
        {/* ── Left: list ── */}
        <div className="faq-list">
          {/* category chips */}
          <div className="faq-chips">
            {["전체", ...FAQ_CATEGORIES].map((c) => (
              <button
                key={c}
                className={`faq-chip ${catFilter === c ? "is-on" : ""}`}
                onClick={() => setCatFilter(c)}
              >
                {c} <span className="faq-chip__ct">{catCounts[c] ?? 0}</span>
              </button>
            ))}
          </div>

          {/* rows */}
          <div className="faq-rows">
            {filtered.map((f) => {
              const ck = CAT_COLOR[f.cat];
              return (
                <div
                  key={f.id}
                  className={`faq-row ${selectedId === f.id && !isNew ? "is-selected" : ""}`}
                  onClick={() => loadEditor(f)}
                >
                  <div className="faq-row__top">
                    <span
                      className="faq-row__cat"
                      style={{ background: `var(--cat-${ck}-bg)`, color: `var(--cat-${ck}-fg)` }}
                    >
                      {f.cat}
                    </span>

                    {/* media badges */}
                    {f.images.length > 0 && (
                      <span className="faq-row__media"><ImageIcon /> {f.images.length}</span>
                    )}
                    {f.yt && <span className="faq-row__media"><Youtube /> 영상</span>}

                    <span className={`faq-row__status ${f.on ? "faq-row__status--on" : ""}`}>
                      <span className="faq-row__dot" />
                      {f.on ? "게시중" : "비공개"}
                    </span>
                  </div>
                  <div className="faq-row__q"><span className="faq-row__qmark">Q</span>{f.q}</div>
                  <div className="faq-row__a">{f.a}</div>
                </div>
              );
            })}
            {filtered.length === 0 && (
              <p className="faq-empty">해당 카테고리에 FAQ가 없습니다.</p>
            )}
          </div>
        </div>

        {/* ── Right: editor ── */}
        <div className="faq-editor">
          <div className="faq-editor__head">
            <PenLine />
            <span className="faq-editor__label">{editorLabel}</span>
            {editorStatus !== null && (
              <span className={`faq-editor__st ${editorStatus ? "faq-editor__st--on" : ""}`}>
                <span className="faq-row__dot" />
                {editorStatus ? "게시중" : "비공개"}
              </span>
            )}
          </div>

          {/* Category */}
          <div className="faq-field">
            <div className="faq-field__label">카테고리</div>
            <Select value={eCat} onValueChange={(v) => setECat(v as FaqCategory)}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {FAQ_CATEGORIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>

          {/* Question */}
          <div className="faq-field">
            <div className="faq-field__label">질문</div>
            <Input
              value={eQ}
              onChange={(e) => setEQ(e.target.value)}
              placeholder="자주 묻는 질문을 입력하세요"
            />
          </div>

          {/* Answer */}
          <div className="faq-field">
            <div className="faq-field__label">답변</div>
            <textarea
              className="faq-textarea"
              value={eA}
              onChange={(e) => setEA(e.target.value)}
              placeholder="답변을 입력하세요"
            />
          </div>

          {/* Images */}
          <div className="faq-field">
            <div className="faq-field__label">설명 이미지</div>
            <div className="faq-imgs">
              {eImages.map((src, i) => (
                <div key={i} className="faq-imgs__item">
                  <img src={src} alt="" />
                  <button
                    className="faq-imgs__rm"
                    onClick={() => setEImages((prev) => prev.filter((_, j) => j !== i))}
                  >
                    <X />
                  </button>
                </div>
              ))}
              <button className="faq-imgs__add" onClick={() => imgInputRef.current?.click()}>
                <Plus />
              </button>
            </div>
            <input
              ref={imgInputRef}
              type="file"
              accept="image/*"
              multiple
              hidden
              onChange={onImgInput}
            />
          </div>

          {/* YouTube */}
          <div className="faq-field">
            <div className="faq-field__label">설명 영상 (YouTube)</div>
            {eYt ? (
              <div className="faq-yt">
                <iframe src={eYt} allowFullScreen title="YouTube" />
                <button className="faq-yt__rm" onClick={() => { setEYt(""); setYtInput(""); }}>
                  <X />
                </button>
              </div>
            ) : (
              <div className="faq-yt-input">
                <Input
                  value={ytInput}
                  onChange={(e) => setYtInput(e.target.value)}
                  placeholder="YouTube URL을 붙여넣으세요"
                  onKeyDown={(e) => { if (e.key === "Enter") handleYtApply(); }}
                />
                <Button variant="outline" size="sm" onClick={handleYtApply} disabled={!ytInput.trim()}>
                  <Youtube /> 적용
                </Button>
              </div>
            )}
          </div>

          {/* Switch box */}
          <div className="faq-switchbox">
            <label className="faq-switchbox__row">
              <div>
                <div className="faq-switchbox__t">게시</div>
                <div className="faq-switchbox__d">사용자 고객센터에 노출</div>
              </div>
              <Switch checked={eOn} onCheckedChange={setEOn} />
            </label>
          </div>

          {/* Actions */}
          <div className="faq-editor__actions">
            <button
              className="faq-editor__del"
              disabled={isNew}
              onClick={handleDelete}
              title="삭제"
            >
              <Trash2 />
            </button>
            <Button
              className="bg-gradient-to-r from-blue-600 to-indigo-600 text-white flex-1"
              size="sm"
              onClick={handleSave}
            >
              <Save /> 저장
            </Button>
          </div>
        </div>
      </div>

      {toast && <Toast msg={toast.msg} tone={toast.tone} />}
    </AdminShell>
  );
}
