import { useState, useEffect, useMemo } from "react";
import { PenSquare } from "lucide-react";
import { useNavigate } from "react-router";
import { PostList } from "../components/PostList";
import { PostFilters, type SortKey, type PeriodKey } from "../components/PostFilters";
import { HotPostsSidebar } from "../components/HotPostsSidebar";
import { PostDetailView } from "../components/PostDetailView";
import { PostEditorForm } from "../components/PostEditorForm";
import { CATEGORIES } from "../types/community";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useAuth } from "@/app/auth/AuthContext";
import type { CommunityPost } from "../types/community";
import "../styles/community.css";

type ViewMode = "list" | "detail" | "write";

const PERIOD_MAX: Record<PeriodKey, number> = { all: Infinity, today: 0, week: 7, month: 31 };

export function CommunityHomePage() {
  const [viewMode, setViewMode] = useState<ViewMode>("list");
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [selectedPost, setSelectedPost] = useState<CommunityPost | null>(null);
  const [sort, setSort] = useState<SortKey>("recent");
  const [period, setPeriod] = useState<PeriodKey>("all");
  const [tag, setTag] = useState("");

  const { posts, loading, fetchPosts } = useCommunityStore();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const filteredPosts = useMemo(() => {
    const maxDays = PERIOD_MAX[period];
    const q = tag.trim().toLowerCase();
    return posts
      .filter((p) => (p.daysAgo ?? 0) <= maxDays)
      .filter((p) => !q || (p.tags ?? []).some((t) => t.toLowerCase().includes(q)))
      .slice()
      .sort((a, b) => {
        if (sort === "recent") return (a.daysAgo ?? 0) - (b.daysAgo ?? 0);
        const key = sort === "likes" ? "likeCount" : sort === "comments" ? "commentCount" : "viewCount";
        return (b.stats[key] ?? 0) - (a.stats[key] ?? 0);
      });
  }, [posts, sort, period, tag]);

  // 카테고리 바뀔 때마다 목록 다시 불러오기
  useEffect(() => {
    const cat = CATEGORIES.find((c) => c.value === selectedCategory);
    fetchPosts(selectedCategory === "all" ? undefined : cat?.slug);
  }, [selectedCategory, fetchPosts]);

  // 브라우저 뒤로가기 지원
  useEffect(() => {
    const onPopState = () => {
      setViewMode("list");
      setSelectedPost(null);
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  const handlePostClick = (post: CommunityPost) => {
    setSelectedPost(post);
    setViewMode("detail");
    window.history.pushState({ view: "detail" }, "");
    window.scrollTo(0, 0);
  };

  const handleBack = () => {
    window.history.back();
  };

  if (viewMode === "detail" && selectedPost) {
    return <PostDetailView postId={selectedPost.id} onBack={handleBack} />;
  }

  if (viewMode === "write") {
    return <PostEditorForm onCancel={handleBack} onSubmit={handleBack} />;
  }

  return (
    <div className="ct-page">
      <div className="ct-pagehead">
        <div className="ct-pagehead__row">
          <div>
            <h1>커뮤니티</h1>
            <p>익명으로 취업·이직·면접 이야기를 나눠보세요.</p>
          </div>
          <button className="ct-btn-brand" onClick={() => {
            if (!isAuthenticated) {
              alert("로그인이 필요합니다.");
              navigate("/login");
              return;
            }
            setViewMode("write");
            window.history.pushState({ view: "write" }, "");
          }}>
            <PenSquare /> 글쓰기
          </button>
        </div>
      </div>

      <div className="ct-board__bar">
        <div className="ct-tabs" role="tablist">
          {CATEGORIES.map((cat) => (
            <button
              key={cat.value}
              role="tab"
              aria-selected={selectedCategory === cat.value}
              className="ct-tab"
              onClick={() => setSelectedCategory(cat.value)}
            >
              {cat.label}
              {cat.count != null && (
                <span className="count">{cat.count.toLocaleString()}</span>
              )}
            </button>
          ))}
        </div>
      </div>

      <PostFilters
        sort={sort} period={period} tag={tag}
        onSortChange={setSort} onPeriodChange={setPeriod} onTagChange={setTag}
      />

      <div className="ct-grid">
        <div>
          {loading ? (
            <p style={{ textAlign: "center", color: "var(--muted-foreground)", padding: "48px 0" }}>
              불러오는 중...
            </p>
          ) : (
            <>
              <PostList posts={filteredPosts} onPostClick={handlePostClick} />
              {filteredPosts.length === 0 && (
                <p style={{ textAlign: "center", color: "var(--muted-foreground)", padding: "48px 0" }}>
                  {posts.length === 0 ? "해당 카테고리에 게시글이 없습니다." : "검색 결과가 없습니다."}
                </p>
              )}
            </>
          )}
        </div>
        <HotPostsSidebar />
      </div>
    </div>
  );
}
