const UNSAFE_NAVIGATION_CHARACTERS = /[\\\u0000-\u001f\u007f\s]/u;

/** React Router로 넘겨도 같은 origin을 벗어나지 않는 앱 내부 절대 경로만 반환한다. */
export function safeInternalAppPath(raw: string | null | undefined): string | null {
  const value = raw?.trim();
  if (!value || !value.startsWith("/") || value.startsWith("//")) return null;
  if (UNSAFE_NAVIGATION_CHARACTERS.test(value)) return null;
  return value;
}

/** 새 문서 이동용 경로. Vite basename을 보존하면서 unsafe 링크는 null로 닫는다. */
export function publicInternalAppPath(raw: string | null | undefined): string | null {
  const path = safeInternalAppPath(raw);
  if (!path) return null;
  return `${import.meta.env.BASE_URL.replace(/\/$/, "")}${path}`;
}

/** 광고의 제품 계약인 외부 http(s)와 안전한 내부 경로만 새 창 대상으로 허용한다. */
export function safeWebOrInternalUrl(raw: string | null | undefined): string | null {
  const internal = publicInternalAppPath(raw);
  if (internal) return internal;

  const value = raw?.trim();
  if (!value || UNSAFE_NAVIGATION_CHARACTERS.test(value)) return null;
  try {
    const parsed = new URL(value);
    if ((parsed.protocol !== "http:" && parsed.protocol !== "https:") || !parsed.hostname) return null;
    return parsed.href;
  } catch {
    return null;
  }
}
