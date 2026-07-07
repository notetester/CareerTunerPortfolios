/**
 * 리치텍스트(HTML) 공통 처리 유틸 — 커뮤니티 글 / FAQ 답변 / 공지 본문이 공유한다.
 * TipTap 에디터가 뱉는 HTML을 XSS 안전하게 정화하고, 기존 비-HTML 본문과 공존시킨다.
 * - 저장 전/렌더 전 모두 sanitize (defense-in-depth)
 * - content에 HTML 태그가 없으면 기존 본문으로 간주 → 호출부에서 평문/마크다운 등으로 폴백 렌더
 */
import DOMPurify from "dompurify";

// TipTap 툴바(볼드/이탤릭/리스트/인용/링크/코드)가 만드는 태그 + 읽기용 .dv-prose가 스타일링하는 태그만 허용
const ALLOWED_TAGS = [
  "p", "br", "strong", "b", "em", "i", "s", "u",
  "ul", "ol", "li", "blockquote", "code", "pre",
  "a", "h1", "h2", "h3",
];
const ALLOWED_ATTR = ["href", "target", "rel"];

// 링크에 안전 속성 강제 (reverse tabnabbing 방지). 모듈당 1회만 등록.
let hookRegistered = false;
function registerLinkHook() {
  if (hookRegistered) return;
  DOMPurify.addHook("afterSanitizeAttributes", (node) => {
    if (node.nodeName === "A") {
      node.setAttribute("target", "_blank");
      node.setAttribute("rel", "noopener noreferrer nofollow");
    }
  });
  hookRegistered = true;
}

/**
 * 사용자 입력 HTML을 XSS 안전하게 정화한다.
 * DOMPurify 기본 정책으로 <script>, on* 이벤트 핸들러, javascript: URI가 제거되고,
 * 위 허용 태그/속성만 남는다.
 */
export function sanitizePostHtml(html: string): string {
  registerLinkHook();
  return DOMPurify.sanitize(html ?? "", {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOW_DATA_ATTR: false,
  });
}

/** content가 신규 HTML 글인지(true) 기존 평문 글인지(false) 판별한다. */
export function isHtmlContent(content: string | null | undefined): boolean {
  return /<\/?[a-z][a-z0-9]*(\s[^>]*)?>/i.test(content ?? "");
}

/** 기존 평문(줄바꿈 보존)을 에디터 로드용 안전 HTML로 변환한다. */
export function plainToHtml(text: string): string {
  const esc = (text ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
  return esc
    .split(/\n{2,}/)
    .map((para) => `<p>${para.replace(/\n/g, "<br>") || "<br>"}</p>`)
    .join("");
}

/** 카드 미리보기용 순수 텍스트 추출 (HTML이면 태그 제거, 평문이면 그대로). */
export function toPlainPreview(content: string | null | undefined): string {
  if (!content) return "";
  if (!isHtmlContent(content)) return content;
  const clean = sanitizePostHtml(content);
  const doc = new DOMParser().parseFromString(clean, "text/html");
  return (doc.body.textContent ?? "").replace(/\s+/g, " ").trim();
}
