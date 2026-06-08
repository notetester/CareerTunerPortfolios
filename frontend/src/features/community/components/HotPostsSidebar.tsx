import { useEffect } from "react";
import { ArrowRight } from "lucide-react";
import { useCommunityStore } from "../hooks/useCommunityStore";

export function HotPostsSidebar() {
  const { hotPosts, fetchHotPosts } = useCommunityStore();

  useEffect(() => {
    fetchHotPosts();
  }, [fetchHotPosts]);

  return (
    <aside className="ct-aside">
      <div className="ct-aside__card">
        <div className="ct-aside__head">
          <h3>오늘의 인기 글</h3>
          <span className="more">더보기</span>
        </div>
        <div className="ct-hot">
          {hotPosts.map((post, i) => (
            <div key={i} className="ct-hot__item">
              <span className="ct-hot__rank">{i + 1}</span>
              <div>
                <div className="ct-hot__t">{post.title}</div>
                <div className="ct-hot__m">
                  댓글 {post.comments} · 조회 {post.views.toLocaleString()}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="ct-promo">
        <h4>이력서, 합격하는 방향으로</h4>
        <p>AI가 강점과 보완점을 짚어드려요. 지금 무료로 분석해보세요.</p>
        <button className="ct-btn-brand" style={{ width: "100%" }}>
          무료 분석 시작 <ArrowRight />
        </button>
      </div>
    </aside>
  );
}
