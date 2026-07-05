import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { ArrowLeft, Lock } from "lucide-react";
import { ActivityList } from "../components/ActivityList";
import { ScrapDetailView } from "../components/ScrapDetailView";
import { ReactionSettingsCard } from "../components/ReactionSettingsCard";
import { Pager } from "../components/Pager";
import { useAuth } from "@/app/auth/AuthContext";
import { toast } from "@/features/notification/components/toast";
import * as communityApi from "../api/communityApi";
import {
  ACTIVITY_TAB_LABELS,
  type ActivityItem, type ActivityPage, type ActivityTabKey, type ScrapItem,
} from "../types/community";
import "../styles/community.css";

const PER = 20;
const TAB_KEYS = Object.keys(ACTIVITY_TAB_LABELS) as ActivityTabKey[];

/**
 * 내 활동 — 작성 글/댓글/답글, 좋아요한 글·댓글, 즐겨찾기, 스크랩을 탭으로 본다.
 * 익명 작성/익명 리액션 항목도 본인 시점에는 표시된다("익명" 칩).
 * 스크랩 항목은 스냅샷 상세를 연다. 하단에 반응 유지/해지 설정 카드.
 */
export function CommunityActivityPage() {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [tab, setTab] = useState<ActivityTabKey>("posts");
  const [page, setPage] = useState(1);
  const [data, setData] = useState<ActivityPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [scrapDetail, setScrapDetail] = useState<ScrapItem | null>(null);

  const load = useCallback(async (t: ActivityTabKey, p: number) => {
    setLoading(true);
    try {
      const result = await communityApi.getMyActivity(t, p - 1, PER);
      setData(result);
    } catch {
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isAuthenticated) void load(tab, page);
  }, [isAuthenticated, tab, page, load]);

  const handleItemClick = async (item: ActivityItem) => {
    if (item.itemType === "SCRAP" && item.scrapId != null) {
      try {
        setScrapDetail(await communityApi.getScrapDetail(item.scrapId));
        window.scrollTo(0, 0);
      } catch {
        toast.error("스크랩을 불러오지 못했습니다.");
      }
      return;
    }
    if (item.postId != null) navigate(`/community/posts/${item.postId}`);
  };

  if (!isAuthenticated) {
    return (
      <div className="cv-page">
        <div className="ct-activity-lock">
          <Lock />
          <p>내 활동은 로그인 후에 볼 수 있어요.</p>
          <button className="av-btn av-btn--ink" onClick={() => navigate("/login")}>로그인하기</button>
        </div>
      </div>
    );
  }

  if (scrapDetail) {
    return (
      <ScrapDetailView
        scrap={scrapDetail}
        onBack={() => setScrapDetail(null)}
        onDeleted={() => {
          setScrapDetail(null);
          void load(tab, page);
        }}
      />
    );
  }

  const totalPages = Math.max(1, Math.ceil((data?.total ?? 0) / PER));

  return (
    <div className="cv-page">
      <div className="uv-phead">
        <div>
          <h1>내 활동</h1>
          <p>내가 쓴 글·댓글과 반응한 글을 한곳에서 확인하세요</p>
        </div>
        <button className="av-btn" style={{ height: 34, padding: "0 14px" }} onClick={() => navigate("/community")}>
          <ArrowLeft /> 커뮤니티
        </button>
      </div>

      <div className="uv-tabs">
        {TAB_KEYS.map((key) => (
          <button
            key={key}
            className={"uv-tab" + (tab === key ? " on" : "")}
            onClick={() => { setTab(key); setPage(1); }}
          >
            {ACTIVITY_TAB_LABELS[key]}
            {tab === key && data != null && <span className="n num">{data.total.toLocaleString()}</span>}
          </button>
        ))}
      </div>

      <div style={{ marginTop: 16 }}>
        {loading ? (
          <p className="av-empty">불러오는 중...</p>
        ) : (
          <>
            <ActivityList
              items={data?.items ?? []}
              emptyText="아직 활동 내역이 없습니다."
              showAnonymousChip
              onItemClick={handleItemClick}
            />
            <Pager page={page} totalPages={totalPages} onPage={setPage} />
          </>
        )}
      </div>

      {/* 반응 유지/해지 설정 — 게시글 수정 시 내 리액션 처리 */}
      <div style={{ marginTop: 28, maxWidth: 560 }}>
        <ReactionSettingsCard />
      </div>
    </div>
  );
}
