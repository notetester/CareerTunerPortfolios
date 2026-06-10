import { useState, useRef, useCallback, useEffect, type DragEvent, type ChangeEvent } from "react";
import {
  Megaphone, Plus, Pin, Eye, PenLine, Send, Save,
  Bold, Italic, List, Quote, Link2, ImagePlus, X,
} from "lucide-react";
import { Input } from "@/app/components/ui/input";
import { Switch } from "@/app/components/ui/switch";
import { Button } from "@/app/components/ui/button";
import AdminShell from "../../../components/AdminShell";
import { type Notice, type NoticeStatus } from "../data/noticesData";
import * as adminNoticeApi from "../api/adminNoticeApi";
import "./admin-notices.css";

type FilterKey = "all" | "published" | "pending";

const FILTERS: { key: FilterKey; label: string }[] = [
  { key: "all", label: "전체" },
  { key: "published", label: "게시중" },
  { key: "pending", label: "대기·임시" },
];

const STATUS_CLS: Record<NoticeStatus, string> = {
  published: "ntc-badge--pub",
  draft: "ntc-badge--draft",
  scheduled: "ntc-badge--sched",
};
const STATUS_LABEL: Record<NoticeStatus, string> = {
  published: "게시중",
  draft: "임시저장",
  scheduled: "예약",
};

/* ── helpers ── */
function readFileAsDataURL(file: File): Promise<string> {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.readAsDataURL(file);
  });
}

function insertMarkdown(
  textarea: HTMLTextAreaElement,
  before: string,
  after: string,
  setText: (v: string) => void,
) {
  const { selectionStart: s, selectionEnd: e, value } = textarea;
  const selected = value.slice(s, e);
  const next = value.slice(0, s) + before + selected + after + value.slice(e);
  setText(next);
  requestAnimationFrame(() => {
    textarea.focus();
    textarea.selectionStart = s + before.length;
    textarea.selectionEnd = s + before.length + selected.length;
  });
}

/* ── Toast ── */
function Toast({ msg, tone }: { msg: string; tone: string }) {
  return <div className={`ntc-toast ntc-toast--${tone}`}>{msg}</div>;
}

/* ── Component ── */
export default function AdminNotices() {
  const [items, setItems] = useState<Notice[]>([]);
  const [filter, setFilter] = useState<FilterKey>("all");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [isNew, setIsNew] = useState(false);

  /* editor state */
  const [eTitle, setETitle] = useState("");
  const [eBody, setEBody] = useState("");
  const [ePinned, setEPinned] = useState(false);
  const [ePublish, setEPublish] = useState(true);
  const [eCover, setECover] = useState<string | null>(null);
  const [eImages, setEImages] = useState<string[]>([]);

  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);
  const bodyRef = useRef<HTMLTextAreaElement>(null);
  const coverInputRef = useRef<HTMLInputElement>(null);
  const imgInputRef = useRef<HTMLInputElement>(null);

  /* 초기 목록 로드 */
  useEffect(() => {
    adminNoticeApi.getNotices().then((list) => {
      setItems(list);
      if (list.length > 0) loadEditor(list[0]);
    }).catch(() => flash("공지 목록을 불러오지 못했습니다.", "red"));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* counts */
  const pubCount = items.filter((n) => n.status === "published").length;
  const pendCount = items.filter((n) => n.status !== "published").length;
  const countMap: Record<FilterKey, number> = { all: items.length, published: pubCount, pending: pendCount };

  const filtered = filter === "all"
    ? items
    : filter === "published"
      ? items.filter((n) => n.status === "published")
      : items.filter((n) => n.status !== "published");

  const selectedItem = items.find((n) => n.id === selectedId) ?? null;
  const editorLabel = isNew ? "새 공지 작성" : "공지 편집";
  const editorStatus = isNew ? null : selectedItem?.status ?? null;

  /* load item into editor */
  const loadEditor = (n: Notice) => {
    setSelectedId(n.id);
    setIsNew(false);
    setETitle(n.title);
    setEBody(n.body);
    setEPinned(n.pinned);
    setEPublish(n.status === "published");
    setECover(n.cover);
    setEImages(n.images);
  };

  /* new notice */
  const handleNew = () => {
    setIsNew(true);
    setSelectedId(null);
    setETitle("");
    setEBody("");
    setEPinned(false);
    setEPublish(true);
    setECover(null);
    setEImages([]);
  };

  /* show toast */
  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  /* save */
  const save = async (asDraft: boolean) => {
    const status = asDraft ? "DRAFT" : "PUBLISHED";
    try {
      if (isNew) {
        const created = await adminNoticeApi.createNotice({
          title: eTitle,
          content: eBody,
          status,
          isPinned: ePinned,
          thumbnailUrl: eCover,
        });
        const newList = await adminNoticeApi.getNotices();
        setItems(newList);
        setSelectedId(created.id);
        setIsNew(false);
      } else if (selectedId) {
        await adminNoticeApi.updateNotice(selectedId, {
          title: eTitle,
          content: eBody,
          status,
          isPinned: ePinned,
          thumbnailUrl: eCover,
        });
        const newList = await adminNoticeApi.getNotices();
        setItems(newList);
      }
      flash(asDraft ? "임시저장했어요" : "공지를 게시했어요", asDraft ? "slate" : "green");
    } catch {
      flash("저장에 실패했습니다.", "red");
    }
  };

  /* row toggles */
  const togglePublish = async (id: number) => {
    const item = items.find((n) => n.id === id);
    if (!item) return;
    const newStatus = item.status === "published" ? "DRAFT" : "PUBLISHED";
    try {
      await adminNoticeApi.updateNotice(id, { status: newStatus });
      const newList = await adminNoticeApi.getNotices();
      setItems(newList);
      if (id === selectedId) setEPublish(newStatus === "PUBLISHED");
    } catch {
      flash("상태 변경에 실패했습니다.", "red");
    }
  };

  const togglePin = async (id: number) => {
    const item = items.find((n) => n.id === id);
    if (!item) return;
    try {
      await adminNoticeApi.updateNotice(id, { isPinned: !item.pinned });
      const newList = await adminNoticeApi.getNotices();
      setItems(newList);
      if (id === selectedId) setEPinned(!item.pinned);
    } catch {
      flash("고정 변경에 실패했습니다.", "red");
    }
  };

  /* cover image */
  const handleCoverFile = async (file: File) => {
    if (!file.type.startsWith("image/")) return;
    const url = await readFileAsDataURL(file);
    setECover(url);
  };

  const onCoverDrop = (e: DragEvent) => {
    e.preventDefault();
    const file = e.dataTransfer.files[0];
    if (file) handleCoverFile(file);
  };

  const onCoverInput = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleCoverFile(file);
    e.target.value = "";
  };

  /* body images */
  const addBodyImages = async (files: FileList) => {
    const urls: string[] = [];
    for (const f of Array.from(files)) {
      if (f.type.startsWith("image/")) urls.push(await readFileAsDataURL(f));
    }
    setEImages((prev) => [...prev, ...urls]);
  };

  const onBodyImgInput = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) addBodyImages(e.target.files);
    e.target.value = "";
  };

  /* markdown toolbar */
  const md = useCallback(
    (before: string, after: string) => {
      if (bodyRef.current) insertMarkdown(bodyRef.current, before, after, setEBody);
    },
    [],
  );

  return (
    <AdminShell
      active="notices"
      breadcrumb="공지사항"
      title="공지사항 관리"
      icon={Megaphone}
      desc="공지사항을 작성하고 게시 상태를 관리합니다."
      actions={
        <Button
          className="bg-gradient-to-r from-blue-600 to-indigo-600 text-white"
          size="sm"
          onClick={handleNew}
        >
          <Plus /> 새 공지
        </Button>
      }
    >
      <div className="ntc-body">
        {/* ── Left: list ── */}
        <div className="ntc-list">
          {/* filter bar */}
          <div className="ntc-list__bar">
            <div className="ntc-seg">
              {FILTERS.map((f) => (
                <button
                  key={f.key}
                  className={`ntc-seg__btn ${filter === f.key ? "is-on" : ""}`}
                  onClick={() => setFilter(f.key)}
                >
                  {f.label} <span className="ntc-seg__ct">{countMap[f.key]}</span>
                </button>
              ))}
            </div>
          </div>

          {/* rows */}
          <div className="ntc-rows">
            {filtered.map((n) => (
              <div
                key={n.id}
                className={`ntc-row ${selectedId === n.id && !isNew ? "is-selected" : ""}`}
                onClick={() => loadEditor(n)}
              >
                {/* thumbnail */}
                <div className="ntc-row__thumb">
                  {n.cover ? (
                    <img src={n.cover} alt="" />
                  ) : (
                    <div className="ntc-row__thumb-placeholder">
                      <Megaphone />
                    </div>
                  )}
                </div>

                <div className="ntc-row__content">
                  <div className="ntc-row__top">
                    {n.pinned && <Pin className="ntc-row__pin" />}
                    <span className={`ntc-badge ${STATUS_CLS[n.status]}`}>
                      {STATUS_LABEL[n.status]}
                    </span>
                  </div>
                  <div className="ntc-row__title">{n.title}</div>
                  <div className="ntc-row__meta">
                    <span>{n.date}</span>
                    {n.views > 0 && <span>· 조회 {n.views.toLocaleString()}</span>}
                    <div className="ntc-row__toggles" onClick={(e) => e.stopPropagation()}>
                      <label className="ntc-toggle-label">
                        <span>게시</span>
                        <Switch
                          checked={n.status === "published"}
                          onCheckedChange={() => togglePublish(n.id)}
                        />
                      </label>
                      <label className="ntc-toggle-label">
                        <span>고정</span>
                        <Switch
                          checked={n.pinned}
                          onCheckedChange={() => togglePin(n.id)}
                        />
                      </label>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* ── Right: editor ── */}
        <div className="ntc-editor">
          <div className="ntc-editor__head">
            <PenLine />
            <span className="ntc-editor__label">{editorLabel}</span>
            {editorStatus && (
              <span className={`ntc-badge ${STATUS_CLS[editorStatus]}`}>
                {STATUS_LABEL[editorStatus]}
              </span>
            )}
          </div>

          {/* Cover image */}
          <div className="ntc-field">
            <div className="ntc-field__label">대표 이미지</div>
            {eCover ? (
              <div className="ntc-cover-preview">
                <img src={eCover} alt="대표 이미지" />
                <button className="ntc-cover-preview__rm" onClick={() => setECover(null)}>
                  <X />
                </button>
              </div>
            ) : (
              <div
                className="ntc-cover-drop"
                onClick={() => coverInputRef.current?.click()}
                onDragOver={(e) => e.preventDefault()}
                onDrop={onCoverDrop}
              >
                <ImagePlus />
                <span>클릭 또는 드래그하여 대표 이미지 업로드</span>
                <span className="ntc-cover-drop__hint">권장 1200×525 (16:7)</span>
              </div>
            )}
            <input
              ref={coverInputRef}
              type="file"
              accept="image/*"
              hidden
              onChange={onCoverInput}
            />
          </div>

          {/* Title */}
          <div className="ntc-field">
            <div className="ntc-field__label">제목</div>
            <Input
              value={eTitle}
              onChange={(e) => setETitle(e.target.value)}
              placeholder="공지 제목을 입력하세요"
            />
          </div>

          {/* Body toolbar + textarea */}
          <div className="ntc-field">
            <div className="ntc-field__label">내용</div>
            <div className="ntc-toolbar">
              <button type="button" title="굵게" onClick={() => md("**", "**")}><Bold /></button>
              <button type="button" title="기울임" onClick={() => md("*", "*")}><Italic /></button>
              <button type="button" title="목록" onClick={() => md("- ", "")}><List /></button>
              <button type="button" title="인용" onClick={() => md("> ", "")}><Quote /></button>
              <button type="button" title="링크" onClick={() => md("[", "](url)")}><Link2 /></button>
              <button type="button" title="이미지 첨부" onClick={() => imgInputRef.current?.click()}>
                <ImagePlus />
              </button>
            </div>
            <textarea
              ref={bodyRef}
              className="ntc-textarea"
              value={eBody}
              onChange={(e) => setEBody(e.target.value)}
              placeholder="마크다운으로 내용을 작성하세요"
            />
            <input
              ref={imgInputRef}
              type="file"
              accept="image/*"
              multiple
              hidden
              onChange={onBodyImgInput}
            />
          </div>

          {/* Body images grid */}
          {(eImages.length > 0 || true) && (
            <div className="ntc-imgs">
              {eImages.map((src, i) => (
                <div key={i} className="ntc-imgs__item">
                  <img src={src} alt="" />
                  <button
                    className="ntc-imgs__rm"
                    onClick={() => setEImages((prev) => prev.filter((_, j) => j !== i))}
                  >
                    <X />
                  </button>
                </div>
              ))}
              <button
                className="ntc-imgs__add"
                onClick={() => imgInputRef.current?.click()}
              >
                <Plus />
              </button>
            </div>
          )}

          {/* Switch box */}
          <div className="ntc-switchbox">
            <label className="ntc-switchbox__row">
              <div>
                <div className="ntc-switchbox__t">상단 고정</div>
                <div className="ntc-switchbox__d">목록 최상단에 핀 노출</div>
              </div>
              <Switch checked={ePinned} onCheckedChange={setEPinned} />
            </label>
            <label className="ntc-switchbox__row">
              <div>
                <div className="ntc-switchbox__t">즉시 게시</div>
                <div className="ntc-switchbox__d">저장과 동시에 노출</div>
              </div>
              <Switch checked={ePublish} onCheckedChange={setEPublish} />
            </label>
          </div>

          {/* Actions */}
          <div className="ntc-editor__actions">
            <Button variant="outline" size="sm" onClick={() => save(true)}>
              <Save /> 임시저장
            </Button>
            <Button
              className="bg-gradient-to-r from-blue-600 to-indigo-600 text-white"
              size="sm"
              onClick={() => save(false)}
            >
              <Send /> 게시하기
            </Button>
          </div>
        </div>
      </div>

      {/* Toast */}
      {toast && <Toast msg={toast.msg} tone={toast.tone} />}
    </AdminShell>
  );
}
