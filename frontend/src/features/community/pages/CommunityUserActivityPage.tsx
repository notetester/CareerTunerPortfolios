import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { ArrowLeft, Lock } from "lucide-react";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { ActivityList } from "../components/ActivityList";
import { Pager } from "../components/Pager";
import { useAuth } from "@/app/auth/AuthContext";
import * as communityApi from "../api/communityApi";
import {
  ACTIVITY_TAB_LABELS,
  type ActivityItem, type ActivityPage, type ActivityTabKey, type ActivityTabs,
} from "../types/community";
import "../styles/community.css";

const PER = 20;
const TAB_KEYS = Object.keys(ACTIVITY_TAB_LABELS) as ActivityTabKey[];

/**
 * 타인 프로필 활동 탭 — 작성자 이름 클릭으로 진입한다.
 * 탭별 공개 여부는 대상의 공개범위 정책(activity.*)을 서버가 평가해 내려주고,
 * 비공개 탭은 잠금 아이콘으로 표시된다. 익명 작성/익명 리액션 항목은 서버가 제외한다.
 */
export function CommunityUserActivityPage() {
  const { userId: userIdParam } = useParams();
  const userId = Number(userIdParam);
  const navigate = useNavigate();
  const { user } = useAuth();
  const [tabs, setTabs] = useState<ActivityTabs | null>(null);
  const [tabsError, setTabsError] = useState<string | null>(null);
  const [tab, setTab] = useState<ActivityTabKey>("posts");
  const [page, setPage] = useState(1);
  const [data, setData] = useState<ActivityPage | null>(null);
  const [loading, setLoading] = useState(false);

  // 본인 프로필로 들어오면 내 활동으로 보낸다(익명 항목까지 보이는 화면).
  useEffect(() => {
    if (user && user.id === userId) navigate("/community/activity", { replace: true });
  }, [user, userId, navigate]);

  useEffect(() => {
    if (!Number.isFinite(userId)) return;
    communityApi.getUserActivityTabs(userId)
      .then((t) => {
        setTabs(t);
        // 첫 공개 탭을 기본 선택
        const firstOpen = TAB_KEYS.find((k) => t.tabs[k]);
        if (firstOpen && !t.tabs.posts) setTab(firstOpen);
      })
      .catch((e) => setTabsError(e instanceof Error ? e.message : "사용자를 찾을 수 없습니다."));
  }, [userId]);

  const load = useCallback(async (t: ActivityTabKey, p: number) => {
    setLoading(true);
    try {
      setData(await communityApi.getUserActivity(userId, t, p - 1, PER));
    } catch {
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [userId]);

  const allowed = tabs?.tabs[tab] ?? true;

  useEffect(() => {
    if (tabs && allowed) void load(tab, page);
  }, [tabs, allowed, tab, page, load]);

  const handleItemClick = (item: ActivityItem) => {
    if (item.postId != null) navigate(`/community/posts/${item.postId}`);
  };

  if (tabsError) {
    return (
      <div className="cv-page">
        <button className="ct-detail__back" onClick={() => navigate(-1)}>
          <ArrowLeft /> 돌아가기
        </button>
        <p className="av-empty">{tabsError}</p>
      </div>
    );
  }

  const totalPages = Math.max(1, Math.ceil((data?.total ?? 0) / PER));

  return (
    <div className="cv-page">
      <button className="ct-detail__back" onClick={() => navigate(-1)}>
        <ArrowLeft /> 돌아가기
      </button>

      <div className="uv-phead" style={{ marginTop: 12 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <Avatar className="w-12 h-12">
            <AvatarFallback className="bg-muted">{(tabs?.name ?? "-")[0]}</AvatarFallback>
          </Avatar>
          <div>
            <h1>{tabs?.name ?? "..."}</h1>
            <p>커뮤니티 활동</p>
          </div>
        </div>
      </div>

      <div className="uv-tabs">
        {TAB_KEYS.map((key) => {
          const open = tabs?.tabs[key] ?? true;
          return (
            <button
              key={key}
              className={"uv-tab" + (tab === key ? " on" : "")}
              onClick={() => { setTab(key); setPage(1); }}
              title={open ? undefined : "비공개 탭입니다"}
              style={open ? undefined : { opacity: 0.55 }}
            >
              {ACTIVITY_TAB_LABELS[key]}
              {!open && <Lock style={{ width: 12, height: 12, marginLeft: 4, verticalAlign: "-1px" }} />}
            </button>
          );
        })}
      </div>

      <div style={{ marginTop: 16 }}>
        {!allowed ? (
          <div className="ct-activity-lock">
            <Lock />
            <p>이 사용자가 비공개로 설정한 활동입니다.</p>
          </div>
        ) : loading || !tabs ? (
          <p className="av-empty">불러오는 중...</p>
        ) : (
          <>
            <ActivityList
              items={data?.items ?? []}
              emptyText="공개된 활동이 없습니다."
              onItemClick={handleItemClick}
            />
            <Pager page={page} totalPages={totalPages} onPage={setPage} />
          </>
        )}
      </div>
    </div>
  );
}
