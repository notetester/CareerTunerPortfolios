import { Search } from "lucide-react";

export type SortKey = "recent" | "likes" | "comments";

interface PostFiltersProps {
  sort: SortKey;
  tag: string;
  onSortChange: (v: SortKey) => void;
  onTagChange: (v: string) => void;
}

const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  { value: "recent", label: "최신" },
  { value: "likes", label: "인기" },
  { value: "comments", label: "댓글" },
];

export function PostFilters({ sort, tag, onSortChange, onTagChange }: PostFiltersProps) {
  return (
    <div className="cv-bar">
      <div className="av-seg">
        {SORT_OPTIONS.map((o) => (
          <button
            key={o.value}
            className={sort === o.value ? "on" : ""}
            onClick={() => onSortChange(o.value)}
          >
            {o.label}
          </button>
        ))}
      </div>
      <div className="right av-search" style={{ width: 200 }}>
        <Search />
        <input
          placeholder="태그·키워드 검색"
          value={tag}
          onChange={(e) => onTagChange(e.target.value)}
        />
      </div>
    </div>
  );
}
