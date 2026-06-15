const KOREA_TIME_ZONE = "Asia/Seoul";
const LOCAL_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const LOCAL_DATE_TIME_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/;

function parseServerDate(value: string): Date {
  const trimmed = value.trim();
  if (LOCAL_DATE_PATTERN.test(trimmed)) {
    return new Date(`${trimmed}T00:00:00+09:00`);
  }
  if (LOCAL_DATE_TIME_PATTERN.test(trimmed)) {
    return new Date(`${trimmed}+09:00`);
  }
  return new Date(trimmed);
}

export function formatKoreaDate(value: string | null | undefined, emptyLabel = "미입력"): string {
  if (!value) return emptyLabel;
  const date = parseServerDate(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeZone: KOREA_TIME_ZONE,
  }).format(date);
}

export function formatKoreaDateTime(value: string | null | undefined, emptyLabel = "미입력"): string {
  if (!value) return emptyLabel;
  const date = parseServerDate(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: KOREA_TIME_ZONE,
  }).format(date);
}
