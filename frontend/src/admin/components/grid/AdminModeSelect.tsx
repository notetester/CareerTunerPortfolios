import type { ReactElement } from "react";
import type { AdminListMode } from "./types";

/** 로드 방식 선택 — SERVER(페이지 로드) / CLIENT(전체 로드, 상한 내). localStorage 영속은 훅이 담당. */
interface AdminModeSelectProps {
  mode: AdminListMode;
  onChange: (mode: AdminListMode) => void;
  disabled?: boolean;
}

export function AdminModeSelect({ mode, onChange, disabled = false }: AdminModeSelectProps): ReactElement {
  return (
    <select
      value={mode}
      onChange={(event) => onChange(event.target.value as AdminListMode)}
      className="h-9 rounded-md border border-slate-200 bg-card px-2 text-sm"
      aria-label="목록 로드 방식"
      disabled={disabled}
      title="페이지 로드=서버 페이징, 전체 로드=상한 내 전량을 받아 즉시 정렬/페이징"
    >
      <option value="SERVER">페이지 로드</option>
      <option value="CLIENT">전체 로드</option>
    </select>
  );
}
