import { useEffect } from "react";
import { Link } from "react-router";
import { UserRound, ChevronRight } from "lucide-react";
import { useCommunityStore } from "../hooks/useCommunityStore";

interface HotPostsSidebarProps {
  /** 내 활동 진입 — 로그인 게이트는 부모(requireAuth)가 처리한다. */
  onActivity: () => void;
  /** 커뮤니티 가이드라인 열기. */
  onGuidelines: () => void;
}

export function HotPostsSidebar({ onActivity, onGuidelines }: HotPostsSidebarProps) {
  const { hotPosts, fetchHotPosts } = useCommunityStore();

  useEffect(() => {
    fetchHotPosts();
  }, [fetchHotPosts]);

  return (
    <aside className="av-rail">
      <section className="av-panel">
        <button type="button" className="cv-raillink" onClick={onActivity}>
          <UserRound /> 내 활동
          <ChevronRight className="cv-raillink__arr" />
        </button>
      </section>
      <section className="av-panel cv-rank">
        <div className="av-mod__h">
          <span className="av-mod__t">주간 인기글</span>
        </div>
        <div className="av-list">
          {hotPosts.map((post, i) => (
            <Link
              key={post.id ?? i} // 구버전 백엔드 응답에는 id가 없어 undefined일 수 있다
              to={`/community/posts/${post.id}`}
            >
              <span className="av-rank num">{i + 1}</span>
              <span style={{ minWidth: 0 }}>
                <span className="av-list__t" style={{ whiteSpace: "normal", lineHeight: 1.4, display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden", fontWeight: 550 }}>
                  {post.title}
                </span>
                <span className="av-list__s num" style={{ display: "block", marginTop: 2 }}>
                  조회 {post.views ?? 0} · 댓글 {post.comments}
                </span>
              </span>
            </Link>
          ))}
        </div>
      </section>
      <section className="av-panel">
        <div className="av-mod__h"><span className="av-mod__t">글쓰기 전에</span></div>
        <div className="av-note" style={{ marginTop: 12 }}>
          회사·개인을 특정한 비방, 허위 정보는 숨김 처리될 수 있어요. <b>면접 후기</b>는 기억나는 질문 위주로 적어주시면 모두에게 도움이 됩니다.
        </div>
        <button type="button" className="cv-guidelink" onClick={onGuidelines}>
          커뮤니티 가이드라인 전체 보기 <ChevronRight />
        </button>
      </section>
    </aside>
  );
}
