import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import {
  ArrowLeft, Eye, Clock, Star,
  Users, Calendar, Gauge, Trash2,
} from "lucide-react";
import { CategoryBadge } from "./CategoryBadge";
import { ReactionButtons } from "./ReactionButtons";
import { CommentSection } from "./CommentSection";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import * as communityApi from "../api/communityApi";
import { relTime } from "@/features/notification/types/notification";

interface PostDetailViewProps {
  postId: number;
  onBack: () => void;
}

function DifficultyStars({ level }: { level: number }) {
  return (
    <span className="ct-stars">
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={n <= level ? "on" : "off"}>
          <Star />
        </span>
      ))}
    </span>
  );
}

function renderInline(text: string): React.ReactNode {
  const parts: React.ReactNode[] = [];
  const regex = /(\*\*(.+?)\*\*|`(.+?)`)/g;
  let lastIndex = 0;
  let match;
  let k = 0;
  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) parts.push(text.slice(lastIndex, match.index));
    if (match[2]) parts.push(<strong key={k}>{match[2]}</strong>);
    else parts.push(<code key={k}>{match[3]}</code>);
    lastIndex = regex.lastIndex;
    k++;
  }
  if (lastIndex < text.length) parts.push(text.slice(lastIndex));
  return parts.length === 1 ? parts[0] : <>{parts}</>;
}

function renderMarkdown(md: string) {
  const lines = md.split("\n");
  const blocks: React.ReactNode[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (line.trim() === "") { i++; continue; }
    if (line.trim().startsWith("```")) {
      const code: string[] = []; i++;
      while (i < lines.length && !lines[i].trim().startsWith("```")) { code.push(lines[i]); i++; }
      i++;
      blocks.push(<pre key={`pre-${i}`}><code>{code.join("\n")}</code></pre>);
      continue;
    }
    if (line.startsWith("### ")) {
      blocks.push(<h3 key={i}>{renderInline(line.slice(4))}</h3>);
      i++; continue;
    }
    if (line.startsWith("## ")) {
      blocks.push(<h2 key={i}>{renderInline(line.slice(3))}</h2>);
      i++; continue;
    }
    if (line.startsWith("> ")) {
      const q = [line.slice(2)]; i++;
      while (i < lines.length && lines[i].startsWith("> ")) { q.push(lines[i].slice(2)); i++; }
      blocks.push(<blockquote key={`q-${i}`}>{renderInline(q.join(" "))}</blockquote>);
      continue;
    }
    if (/^[-*] /.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^[-*] /.test(lines[i])) { items.push(lines[i].replace(/^[-*] /, "")); i++; }
      blocks.push(
        <ul key={`ul-${i}`}>
          {items.map((it, x) => <li key={x}>{renderInline(it)}</li>)}
        </ul>
      );
      continue;
    }
    if (/^\d+\. /.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\d+\. /.test(lines[i])) { items.push(lines[i].replace(/^\d+\. /, "")); i++; }
      blocks.push(
        <ol key={`ol-${i}`}>
          {items.map((it, x) => <li key={x}>{renderInline(it)}</li>)}
        </ol>
      );
      continue;
    }
    const para = [line]; i++;
    while (i < lines.length && lines[i].trim() !== "" && !/^(#|>|[-*] |\d+\. |```)/.test(lines[i])) { para.push(lines[i]); i++; }
    blocks.push(<p key={`p-${i}`}>{renderInline(para.join(" "))}</p>);
  }
  return blocks;
}

const RESULT_LABELS: Record<string, string> = {
  PASSED: "최종합격", FAILED: "불합격", PENDING: "대기중", UNKNOWN: "비공개",
};

export function PostDetailView({ postId, onBack }: PostDetailViewProps) {
  const { currentPost: d, comments, detailLoading, error, fetchPostDetail, fetchComments } = useCommunityStore();
  const navigate = useNavigate();
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);

  useEffect(() => {
    fetchPostDetail(postId);
    fetchComments(postId);
  }, [postId, fetchPostDetail, fetchComments]);

  const handleDelete = async () => {
    try {
      await communityApi.deletePost(postId);
      setShowDeleteDialog(false);
      onBack();
    } catch {
      setShowDeleteDialog(false);
    }
  };

  if (detailLoading) {
    return (
      <div className="ct-page ct-detail">
        <button className="ct-detail__back" onClick={onBack}>
          <ArrowLeft /> 커뮤니티 목록
        </button>
        <p style={{ textAlign: "center", color: "var(--muted-foreground)", padding: "48px 0" }}>
          불러오는 중...
        </p>
      </div>
    );
  }

  if (!d) {
    return (
      <div className="ct-page ct-detail">
        <button className="ct-detail__back" onClick={onBack}>
          <ArrowLeft /> 커뮤니티 목록
        </button>
        <p style={{ textAlign: "center", color: "var(--muted-foreground)", padding: "48px 0" }}>
          {error ?? "게시글을 불러올 수 없습니다."}
        </p>
      </div>
    );
  }

  const iv = d.interviewReview;
  const isInterview = !!iv;
  const resultLabel = iv?.resultStatus ? RESULT_LABELS[iv.resultStatus] : d.result;

  return (
    <div className="ct-page ct-detail">
      {/* Back */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <button className="ct-detail__back" onClick={onBack}>
          <ArrowLeft /> 커뮤니티 목록
        </button>
        <button
          className="av-btn"
          style={{ color: "var(--av-red, #dc2626)" }}
          onClick={() => setShowDeleteDialog(true)}
        >
          <Trash2 /> 삭제
        </button>
      </div>

      {/* Head */}
      <div className="ct-detail__head">
        <div className="ct-detail__tags">
          <CategoryBadge label={d.categoryLabel} />
          {resultLabel && (
            <span className="ct-badge ct-badge--success">{resultLabel}</span>
          )}
        </div>
        <h1 className="ct-detail__title">{d.title}</h1>

        <div className="ct-detail__byline">
          <Avatar className="w-10 h-10">
            <AvatarFallback className="bg-muted text-sm">{d.author.name[0]}</AvatarFallback>
          </Avatar>
          <div className="ct-detail__who">
            <div className="ct-detail__name">
              {d.author.name}
            </div>
            <div className="ct-detail__sub">
              <span><Clock />{relTime(d.createdAt)}</span>
              <span><Eye />조회 {d.stats.viewCount.toLocaleString()}</span>
            </div>
          </div>
        </div>
      </div>

      <hr style={{ border: "none", borderTop: "1px solid var(--border)", margin: "20px 0" }} />

      {/* Interview meta card */}
      {isInterview && iv && (
        <div className="ct-imeta">
          <div className="ct-imeta__top">
            <Avatar className="w-10 h-10">
              <AvatarFallback className="text-sm font-bold">{iv.companyName[0]}</AvatarFallback>
            </Avatar>
            <div>
              <div className="ct-imeta__co">{iv.companyName}</div>
              <div className="ct-imeta__pos">{iv.jobRole}</div>
            </div>
          </div>
          <div className="ct-imeta__grid">
            {[
              { icon: Users, label: "면접 유형", value: iv.interviewType },
              { icon: Calendar, label: "면접일", value: iv.interviewDate },
              { icon: Gauge, label: "체감 난이도", value: null, stars: iv.difficulty },
            ].map((cell, idx) => (
              <div key={idx} className="ct-imeta__cell">
                <div className="ct-imeta__k">
                  <cell.icon />{cell.label}
                </div>
                <div className="ct-imeta__v">
                  {cell.stars ? <DifficultyStars level={cell.stars} /> : cell.value ?? "-"}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Body */}
      <div className="ct-prose">{renderMarkdown(d.content)}</div>

      {/* Action bar */}
      <ReactionButtons
        key={`${d.id}-${d.liked}-${d.bookmarked}`}
        postId={d.id}
        likeCount={d.stats.likeCount}
        bookmarkCount={d.stats.bookmarkCount}
        initialLiked={d.liked ?? false}
        initialBookmarked={d.bookmarked ?? false}
      />

      {/* Comments */}
      <CommentSection postId={d.id} comments={comments} />

      {/* Delete dialog */}
      {showDeleteDialog && (
        <ConfirmDialog
          variant="danger"
          icon={<Trash2 />}
          title="이 글을 삭제할까요?"
          description={`삭제하면 댓글 ${comments.length}개와 좋아요도 함께 사라지며 되돌릴 수 없어요.`}
          confirmLabel="삭제"
          cancelLabel="취소"
          onConfirm={handleDelete}
          onCancel={() => setShowDeleteDialog(false)}
        />
      )}
    </div>
  );
}
