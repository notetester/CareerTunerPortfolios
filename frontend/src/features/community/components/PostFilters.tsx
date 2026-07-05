import { Search, Sparkles } from "lucide-react";

export type SortKey = "personalized" | "recent" | "likes" | "comments";

interface PostFiltersProps {
  sort: SortKey;
  tag: string;
  onSortChange: (v: SortKey) => void;
  onTagChange: (v: string) => void;
  /** 개인화("맞춤") 정렬 노출 여부 — 로그인 사용자에게만 보인다. 비로그인은 옵션 숨김. */
  showPersonalized?: boolean;
}

const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  { value: "recent", label: "최신" },
  { value: "likes", label: "인기" },
  { value: "comments", label: "댓글" },
];

export function PostFilters({ sort, tag, onSortChange, onTagChange, showPersonalized }: PostFiltersProps) {
  return (
    <div className="cv-bar">
      <div className="av-seg">
        {showPersonalized && (
          <button
            className={sort === "personalized" ? "on" : ""}
            onClick={() => onSortChange("personalized")}
            title="내 희망 직무·관심사에 맞춘 추천 피드"
          >
            <Sparkles size={13} style={{ marginRight: 4, verticalAlign: "-2px" }} />
            맞춤
          </button>
        )}
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
