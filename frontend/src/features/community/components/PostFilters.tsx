import { Search, SlidersHorizontal } from "lucide-react";

export type SortKey = "recent" | "likes" | "comments" | "views";
export type PeriodKey = "all" | "today" | "week" | "month";

interface PostFiltersProps {
  sort: SortKey;
  period: PeriodKey;
  tag: string;
  onSortChange: (v: SortKey) => void;
  onPeriodChange: (v: PeriodKey) => void;
  onTagChange: (v: string) => void;
}

const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  { value: "recent", label: "최신순" },
  { value: "likes", label: "인기순" },
  { value: "comments", label: "댓글순" },
  { value: "views", label: "조회순" },
];

const PERIOD_OPTIONS: { value: PeriodKey; label: string }[] = [
  { value: "all", label: "전체" },
  { value: "today", label: "오늘" },
  { value: "week", label: "이번 주" },
  { value: "month", label: "이번 달" },
];

export function PostFilters({ sort, period, tag, onSortChange, onPeriodChange, onTagChange }: PostFiltersProps) {
  return (
    <div className="ct-filters">
      <div className="ct-filters__left">
        <SlidersHorizontal className="ct-filters__ic" />
        <div className="ct-filters__group">
          {SORT_OPTIONS.map((o) => (
            <button
              key={o.value}
              className={`ct-filters__chip ${sort === o.value ? "is-on" : ""}`}
              onClick={() => onSortChange(o.value)}
            >
              {o.label}
            </button>
          ))}
        </div>
        <span className="ct-filters__sep" />
        <div className="ct-filters__group">
          {PERIOD_OPTIONS.map((o) => (
            <button
              key={o.value}
              className={`ct-filters__chip ct-filters__chip--period ${period === o.value ? "is-on" : ""}`}
              onClick={() => onPeriodChange(o.value)}
            >
              {o.label}
            </button>
          ))}
        </div>
      </div>
      <div className="ct-filters__search">
        <Search />
        <input
          placeholder="태그 검색"
          value={tag}
          onChange={(e) => onTagChange(e.target.value)}
        />
      </div>
    </div>
  );
}
