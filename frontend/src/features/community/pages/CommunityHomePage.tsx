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

/** URL ?sort= 값이 유효한 정렬 키인지 — 헤더 "인기글"(?sort=likes) 등 딥링크 진입에 쓴다. */
function toSortKey(v: string | null): SortKey | null {
  return v === "recent" || v === "likes" || v === "comments" || v === "personalized" ? v : null;
}

/**
 * 프론트 정렬 키 → 서버 sort 파라미터. 서버 페이지네이션이라 정렬은 서버(findAll)가 처리한다.
 * - personalized: 검색어 없을 때만(7:3 혼합 피드). 검색어와 함께면 최신으로 폴백.
 * - likes → popular(like_count), comments → comment_count, recent → 최신(created_at).
 */
function toServerSort(sort: SortKey, keyword: string): string {
  if (sort === "personalized" && !keyword) return "personalized";
  if (sort === "likes") return "popular";
  if (sort === "comments") return "comments";
  return "latest";
}

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

  // URL ?sort= 로 진입/변경되면 해당 정렬로 연다(헤더 "인기글" → ?sort=likes 딥링크). page 도 1로 리셋.
  useEffect(() => {
    const s = toSortKey(searchParams.get("sort"));
    if (s) { setSort(s); setPage(1); }
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
  const [sort, setSort] = useState<SortKey>(toSortKey(searchParams.get("sort")) ?? "recent");
  const [tag, setTag] = useState("");
  const [page, setPage] = useState(1);
  const PER = 20;

  const { posts, total, loading, error, fetchPosts, fetchPostsByIds, categoryCounts, fetchCategoryCounts } = useCommunityStore();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel, isAuthenticated } = useLoginDialog();

  // ?ids=1,2,3 — 챗봇 추천 글 모아보기 진입. 서버 정확 조회(fetchPostsByIds)로 posts 를 채운다
  // (최신 100건 상한과 무관하게 오래된 추천 글도 정확히 조회, 차단·블라인드 필터는 서버가 동일 적용).
  const recommendedIds = useMemo(() => {
    const raw = searchParams.get("ids");
    if (!raw) return null;
    const ids = raw.split(",").map((s) => Number(s)).filter((n) => Number.isFinite(n) && n > 0);
    return ids.length ? ids : null;
  }, [searchParams]);

  // 정렬·검색·페이지네이션은 모두 서버가 처리한다 → posts 는 현재 페이지 그대로 렌더(클라 재정렬/재슬라이스 없음).
  // 추천 모아보기(?ids=)만 서버가 매칭 전부(≤20건)를 한 번에 내려주므로 클라에서 페이지로 나눈다.
  // 비로그인 상태에서 개인화가 선택돼 있으면 최신 정렬로 폴백(옵션 자체는 숨김).
  useEffect(() => {
    if (!isAuthenticated && sort === "personalized") { setSort("recent"); setPage(1); }
  }, [isAuthenticated, sort]);

  const listCount = recommendedIds ? posts.length : total;
  const totalPages = Math.max(1, Math.ceil(listCount / PER));
  const cur = Math.min(page, totalPages);
  const pagePosts = recommendedIds ? posts.slice((cur - 1) * PER, cur * PER) : posts;

  // 탭/정렬/태그 변경 시 1페이지로 리셋 — 필터 변경과 page 리셋을 같은 핸들러에서 함께 호출해
  // (별도 effect로 분리하지 않음) fetch effect가 중간 페이지로 한 번 더 요청하는 레이스를 없앤다.
  const changeCategory = (v: string) => { setSelectedCategory(v); setPage(1); };
  const changeSort = (s: SortKey) => { setSort(s); setPage(1); };
  const changeTag = (t: string) => { setTag(t); setPage(1); };

  useEffect(() => {
    // 추천 모아보기(?ids=) — 일반 목록 대신 id 정확 조회. 배너 "전체 글 보기"가 ids 를 지우면 아래 일반 경로로 복귀.
    if (recommendedIds) {
      fetchPostsByIds(recommendedIds);
      return;
    }
    const cat = CATEGORIES.find((c) => c.value === selectedCategory);
    const slug = selectedCategory === "all" ? undefined : cat?.slug;
    const kw = tag.trim();
    // 정렬은 서버가 처리한다(likes→popular, comments, recent→latest, personalized는 검색어 없을 때만).
    const serverSort = toServerSort(sort, kw);
    // 검색어 입력은 디바운스(300ms)로 서버 조회, 카테고리 변경·검색어 클리어·정렬·페이지 전환은 즉시.
    // page 는 1-based(화면) → 서버는 0-based 이므로 page-1 로 전달. size=PER 로 서버 페이지네이션.
    const t = setTimeout(() => {
      fetchPosts(slug, serverSort, kw || undefined, page - 1, PER);
    }, kw ? 300 : 0);
    return () => clearTimeout(t);
  }, [selectedCategory, tag, sort, page, fetchPosts, fetchPostsByIds, recommendedIds]);

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

  // 헤더에서 뺀 보조 진입(내 활동·가이드라인) — 레일과 모바일 유틸 행에서 공유한다.
  const goActivity = () => requireAuth(() => navigate("/community/activity"));
  const goGuidelines = () => {
    setViewMode("guidelines");
    window.history.pushState({ view: "guidelines" }, "");
    window.scrollTo(0, 0);
  };
  const goWrite = () => requireAuth(() => {
    setViewMode("write");
    window.history.pushState({ view: "write" }, "");
  });

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

  // 작성/수정 완료 후 목록 1페이지로 복귀 — 새 글이 최신 첫 페이지에 오고, 서버 페이지네이션과 정합을 맞춘다.
  const handleSubmitDone = () => { setPage(1); handleBack(); };

  if (viewMode === "edit" && editData) {
    return (
      <PostEditorForm
        editData={editData}
        onCancel={handleBack}
        onSubmit={handleSubmitDone}
      />
    );
  }

  if (viewMode === "write") {
    return <PostEditorForm onCancel={handleBack} onSubmit={handleSubmitDone} />;
  }

  return (
    <div className="cv-page">
      <div className="uv-phead">
        <div>
          <h1>커뮤니티</h1>
          <p>익명으로 취업·이직·면접 이야기를 나눠보세요</p>
        </div>
        <button className="av-btn av-btn--ink" style={{ height: 34, padding: "0 14px" }} onClick={goWrite}>
          <PenLine /> 글쓰기
        </button>
      </div>

      <div className="uv-tabs">
        {CATEGORIES.map((cat) => (
          <button
            key={cat.value}
            className={"uv-tab" + (selectedCategory === cat.value ? " on" : "")}
            onClick={() => changeCategory(cat.value)}
          >
            {cat.label}
            {cat.value !== "all" && categoryCounts[cat.slug] != null && (
              <span className="n num">{categoryCounts[cat.slug].toLocaleString()}</span>
            )}
          </button>
        ))}
      </div>

      {/* 모바일: 우측 레일이 숨겨지므로 내 활동·가이드라인 진입로를 여기서 유지한다. */}
      <div className="cv-mobutil">
        <button className="av-btn" style={{ height: 32 }} onClick={goActivity}>
          <UserRound /> 내 활동
        </button>
        <button className="av-btn" style={{ height: 32 }} onClick={goGuidelines}>
          <BookOpen /> 가이드라인
        </button>
      </div>

      <div className="cv-grid">
        <div>
          <PostFilters
            sort={sort} tag={tag}
            onSortChange={changeSort} onTagChange={changeTag}
            showPersonalized={isAuthenticated}
          />
          {recommendedIds && (
            // 챗봇 추천 글 모아보기 배너 — 필터 상태를 드러내고 한 번에 해제할 수 있게.
            <div className="flex items-center justify-between gap-2 mb-3 px-3.5 py-2.5 rounded-xl border border-primary/30 bg-primary/10 text-[13px]">
              <span className="font-semibold text-primary">챗봇 추천 글 {posts.length}개만 보고 있어요</span>
              <button
                onClick={() => setSearchParams((p) => { p.delete("ids"); return p; }, { replace: true })}
                className="shrink-0 px-2.5 py-1 rounded-lg text-[12px] font-bold text-primary hover:bg-primary/20 transition-colors">
                전체 글 보기
              </button>
            </div>
          )}
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
              {pagePosts.length === 0 && (
                <p className="av-empty">
                  {recommendedIds
                    ? "추천 글이 삭제되었거나 볼 수 없는 상태예요. 전체 글 보기로 돌아가 주세요."
                    : tag.trim() ? "검색 결과가 없습니다." : "해당 카테고리에 게시글이 없습니다."}
                </p>
              )}
            </>
          )}
        </div>
        <HotPostsSidebar onActivity={goActivity} onGuidelines={goGuidelines} />
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
