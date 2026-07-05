import {
  FileText, MessageSquare, CornerDownRight, Heart, Star, ClipboardList, VenetianMask,
} from "lucide-react";
import { relTime } from "@/features/notification/types/notification";
import type { ActivityItem } from "../types/community";

const ITEM_ICONS: Record<ActivityItem["itemType"], typeof FileText> = {
  POST: FileText,
  COMMENT: MessageSquare,
  REPLY: CornerDownRight,
  SCRAP: ClipboardList,
};

const REACTION_LABELS: Record<string, string> = {
  LIKE: "좋아요",
  DISLIKE: "싫어요",
  RECOMMEND: "추천",
  DISRECOMMEND: "비추천",
  BOOKMARK: "즐겨찾기",
};

interface ActivityListProps {
  items: ActivityItem[];
  emptyText: string;
  /** 본인 시점 목록이면 true — 익명 항목에 "익명" 칩을 붙인다(타인 시점은 서버가 이미 제외). */
  showAnonymousChip?: boolean;
  onItemClick: (item: ActivityItem) => void;
}

/** 활동 목록 공통 렌더 — 내 활동 페이지와 타인 프로필 활동 탭이 공유한다. */
export function ActivityList({ items, emptyText, showAnonymousChip = false, onItemClick }: ActivityListProps) {
  if (items.length === 0) {
    return <p className="av-empty">{emptyText}</p>;
  }
  return (
    <div>
      {items.map((item, idx) => {
        const Icon = item.reactionType === "LIKE" ? Heart
          : item.reactionType === "BOOKMARK" ? Star
          : ITEM_ICONS[item.itemType];
        return (
          <div
            key={`${item.itemType}-${item.scrapId ?? item.commentId ?? item.postId ?? idx}`}
            className="ct-activity-item"
            onClick={() => onItemClick(item)}
          >
            <Icon style={{ width: 18, height: 18, flexShrink: 0, marginTop: 2, color: "var(--muted-foreground)" }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="ct-activity-item__title">{item.title}</div>
              {item.preview && <div className="ct-activity-item__preview">{item.preview}</div>}
              <div className="ct-activity-item__meta">
                {item.reactionType && REACTION_LABELS[item.reactionType] && (
                  <span>{REACTION_LABELS[item.reactionType]}</span>
                )}
                <span>{relTime(item.createdAt)}</span>
                {showAnonymousChip && item.anonymous && (
                  <span className="ct-anon-chip">
                    <VenetianMask style={{ width: 12, height: 12 }} /> 익명 — 다른 사람에게 보이지 않음
                  </span>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
