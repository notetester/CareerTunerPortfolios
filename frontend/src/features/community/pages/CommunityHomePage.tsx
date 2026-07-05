import { useState, useEffect, useMemo } from "react";
import { useSearchParams, useParams, useNavigate } from "react-router";
import { PenLine, Lock, BookOpen, UserRound } from "lucide-react";
import { PostList } from "../components/PostList";
import { Pager } from "../components/Pager";
import { PostFilters, type SortKey } from "../components/PostFilters";
import { HotPostsSidebar } from "../components/HotPostsSidebar";
import { PostDetailView } from "../components/PostDetailView";
import { PostEditorForm } from "../components/PostEditorForm";
import { CommunityGuidelinesPage } from "./CommunityGuidelinesPage";
import { CATEGORIES } from "../types/community";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useLoginDialog } from "../hooks/useLoginDialog";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import type { CommunityPost, CommunityCategory } from "../types/community";
import { CATEGORY_META } from "../types/community";
import type { PostEditData } from "../components/PostEditorForm";
import "../styles/community.css";

type ViewMode = "list" | "detail" | "write" | "edit" | "guidelines";

export function CommunityHomePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { postId: postIdParam } = useParams();
  const navigate = useNavigate();
  const deepLinkPostId = postIdParam ? Number(postIdParam) : null;
  const initialView = searchParams.get("view") === "guidelines"
    ? "guidelines" as ViewMode
    : deepLinkPostId != null ? "detail" as ViewMode : "list" as ViewMode;
  const [viewMode, setViewMode] = useState<ViewMode>(initialView);

  // URL ?view=guidelines 변경 감지
  useEffect(() => {
    if (searchParams.get("view") === "guidelines" && viewMode !== "guidelines") {
      setViewMode("guidelines");
    }
  }, [searchParams]);

  // /community/posts/:postId 딥링크(알림 클릭 등) → 상세 뷰로 진입
  useEffect(() => {
    if (deepLinkPostId != null && !Number.isNaN(deepLinkPostId)) {
      setViewMode("detail");
      window.scrollTo(0, 0);
    }
  }, [deepLinkPostId]);
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [selectedPost, setSelectedPost] = useState<CommunityPost | null>(null);
  const [editData, setEditData] = useState<PostEditData | null>(null);
  const [sort, setSort] = useState<SortKey>("recent");
  const [tag, setTag] = useState("");
  const [page, setPage] = useState(1);
  const PER = 8;

  const { posts, loading, error, fetchPosts, categoryCounts, fetchCategoryCounts } = useCommunityStore();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel } = useLoginDialog();

  // 검색(keyword)은 서버에서 필터된 posts 로 들어오므로 여기선 정렬만(클라 keyword 필터 제거 — 최신 100건 한정 누락 해소).
  const filteredPosts = useMemo(() => {
    return posts
      .slice()
      .sort((a, b) => {
        if (sort === "recent") return (a.daysAgo ?? 0) - (b.daysAgo ?? 0);
        const key = sort === "likes" ? "likeCount" : "commentCount";
        return (b.stats[key] ?? 0) - (a.stats[key] ?? 0);
      });
  }, [posts, sort]);

  const totalPages = Math.max(1, Math.ceil(filteredPosts.length / PER));
  const cur = Math.min(page, totalPages);
  const pagePosts = filteredPosts.slice((cur - 1) * PER, cur * PER);

  // 탭/정렬/태그가 바뀌면 1페이지로 리셋
  useEffect(() => { setPage(1); }, [selectedCategory, sort, tag]);

  useEffect(() => {
    const cat = CATEGORIES.find((c) => c.value === selectedCategory);
    const slug = selectedCategory === "all" ? undefined : cat?.slug;
    const kw = tag.trim();
    // 검색어 입력은 디바운스(300ms)로 서버 조회, 카테고리 변경·검색어 클리어는 즉시.
    const t = setTimeout(() => {
      fetchPosts(slug, undefined, kw || undefined);
    }, kw ? 300 : 0);
    return () => clearTimeout(t);
  }, [selectedCategory, tag, fetchPosts]);

  // 탭 뱃지용 카테고리별 글 수 (목록과 동일 소스에서 집계)
  useEffect(() => {
    fetchCategoryCounts();
  }, [fetchCategoryCounts]);

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
    // 딥링크(/community/posts/:postId)로 진입한 경우 목록 URL로 복귀
    if (deepLinkPostId != null) {
      setSelectedPost(null);
      setViewMode("list");
      navigate("/community", { replace: true });
      return;
    }
    // 쿼리 파라미터로 진입한 경우 정리
    if (searchParams.has("view")) {
      setSearchParams({}, { replace: true });
      setViewMode("list");
      return;
    }
    window.history.back();
  };

  if (viewMode === "guidelines") {
    return <CommunityGuidelinesPage onBack={handleBack} />;
  }

  const detailPostId = selectedPost?.id ?? deepLinkPostId;
  if (viewMode === "detail" && detailPostId != null) {
    return (
      <PostDetailView
        postId={detailPostId}
        onBack={handleBack}
        onEdit={() => {
          const post = useCommunityStore.getState().currentPost;
          if (!post) return;
          const catMeta = CATEGORY_META[post.categoryLabel];
          setEditData({
            id: post.id,
            category: catMeta?.value ?? "free",
            title: post.title,
            content: post.content,
            tags: post.tags ?? [],
            anonymous: post.author.isAnonymous,
            interviewReview: post.interviewReview
              ? {
                  companyName: post.interviewReview.companyName,
                  jobRole: post.interviewReview.jobRole,
                  interviewType: post.interviewReview.interviewType,
                  difficulty: post.interviewReview.difficulty,
                  interviewDate: post.interviewReview.interviewDate,
                  resultStatus: post.interviewReview.resultStatus,
                  questions: post.interviewReview.questions,
                }
              : undefined,
          });
          setViewMode("edit");
          window.history.pushState({ view: "edit" }, "");
          window.scrollTo(0, 0);
        }}
      />
    );
  }

  if (viewMode === "edit" && editData) {
    return (
      <PostEditorForm
        editData={editData}
        onCancel={handleBack}
        onSubmit={handleBack}
      />
    );
  }

  if (viewMode === "write") {
    return <PostEditorForm onCancel={handleBack} onSubmit={handleBack} />;
  }

  return (
    <div className="cv-page">
      <div className="uv-phead">
        <div>
          <h1>커뮤니티</h1>
          <p>익명으로 취업·이직·면접 이야기를 나눠보세요</p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button className="av-btn" style={{ height: 34, padding: "0 14px" }} onClick={() => {
            requireAuth(() => navigate("/community/activity"));
          }}>
            <UserRound /> 내 활동
          </button>
          <button className="av-btn" style={{ height: 34, padding: "0 14px" }} onClick={() => {
            setViewMode("guidelines");
            window.history.pushState({ view: "guidelines" }, "");
            window.scrollTo(0, 0);
          }}>
            <BookOpen /> 가이드라인
          </button>
          <button className="av-btn av-btn--ink" style={{ height: 34, padding: "0 14px" }} onClick={() => {
            requireAuth(() => {
              setViewMode("write");
              window.history.pushState({ view: "write" }, "");
            });
          }}>
            <PenLine /> 글쓰기
          </button>
        </div>
      </div>

      <div className="uv-tabs">
        {CATEGORIES.map((cat) => (
          <button
            key={cat.value}
            className={"uv-tab" + (selectedCategory === cat.value ? " on" : "")}
            onClick={() => setSelectedCategory(cat.value)}
          >
            {cat.label}
            {cat.value !== "all" && categoryCounts[cat.slug] != null && (
              <span className="n num">{categoryCounts[cat.slug].toLocaleString()}</span>
            )}
          </button>
        ))}
      </div>

      <div className="cv-grid">
        <div>
          <PostFilters
            sort={sort} tag={tag}
            onSortChange={setSort} onTagChange={setTag}
          />
          {loading ? (
            <p className="av-empty">불러오는 중...</p>
          ) : error ? (
            <p className="av-empty" style={{ color: "var(--destructive)" }}>
              게시글을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.
            </p>
          ) : (
            <>
              <PostList posts={pagePosts} onPostClick={handlePostClick} />
              <Pager page={cur} totalPages={totalPages} onPage={setPage} />
              {filteredPosts.length === 0 && (
                <p className="av-empty">
                  {tag.trim() ? "검색 결과가 없습니다." : "해당 카테고리에 게시글이 없습니다."}
                </p>
              )}
            </>
          )}
        </div>
        <HotPostsSidebar />
      </div>

      {showLoginDialog && (
        <ConfirmDialog
          variant="info"
          icon={<Lock />}
          title="로그인이 필요해요"
          description="글을 쓰려면 로그인이 필요합니다. 30초면 시작할 수 있어요."
          confirmLabel="로그인하기"
          cancelLabel="둘러보기"
          onConfirm={onLoginConfirm}
          onCancel={onLoginCancel}
        />
      )}
    </div>
  );
}
