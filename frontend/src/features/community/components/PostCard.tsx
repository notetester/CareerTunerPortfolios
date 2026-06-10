import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { Heart, MessageCircle, Eye, Flame } from "lucide-react";
import { CategoryBadge } from "./CategoryBadge";
import { relTime } from "@/features/notification/types/notification";
import type { CommunityPost } from "../types/community";

interface PostCardProps {
  post: CommunityPost;
  onClick?: () => void;
}

export function PostCard({ post, onClick }: PostCardProps) {
  return (
    <div className="ct-post" onClick={onClick}>
      <div className="ct-post__top">
        <CategoryBadge label={post.categoryLabel} />
        {post.result && (
          <span className="ct-badge ct-badge--success ct-badge--dot">{post.result}</span>
        )}
        {post.isHot && (
          <span className="ct-badge ct-badge--solid">
            <Flame /> 인기
          </span>
        )}
        {(post.companyName || post.jobRole) && (
          <span className="ct-post__co">
            {[post.companyName, post.jobRole].filter(Boolean).join(" · ")}
          </span>
        )}
      </div>
      <div className="ct-post__title">{post.title}</div>
      <div className="ct-post__body">{post.content}</div>
      <div className="ct-post__foot">
        <div className="ct-post__author">
          <Avatar className="w-6 h-6">
            <AvatarFallback className="text-[10px] bg-muted">{post.author.name[0]}</AvatarFallback>
          </Avatar>
          {post.author.name} · {relTime(post.createdAt)}
        </div>
        <div className="ct-post__meta">
          <span><Heart /> {post.stats.likeCount}</span>
          <span><MessageCircle /> {post.stats.commentCount}</span>
          <span><Eye /> {post.stats.viewCount.toLocaleString()}</span>
        </div>
      </div>
    </div>
  );
}
