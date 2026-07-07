import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import {
  ArrowLeft, Eye, Clock, Star,
  Users, Calendar, Gauge, Trash2, Pencil, UserX, Lock,
} from "lucide-react";
import { Sparkles } from "lucide-react";
// 개인 차단 진입점 — 익명 글은 작성자 id 가 클라이언트에 없어 게시글 id 로 차단한다(익명성 유지).
import { blockUser, blockUserByContent } from "@/features/privacy/api/privacyApi";
import { showBlockManageToast } from "@/features/privacy/components/blockToast";
import { CategoryBadge } from "./CategoryBadge";
import { ReactionButtons } from "./ReactionButtons";
import { CommentSection } from "./CommentSection";
import { useCommunityStore } from "../hooks/useCommunityStore";
import { useLoginDialog } from "../hooks/useLoginDialog";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import { toast } from "@/features/notification/components/toast";
import * as communityApi from "../api/communityApi";
import type { ParsedAiTags } from "../api/communityApi";
import { useAuth } from "@/app/auth/AuthContext";
import { relTime } from "@/features/notification/types/notification";

interface PostDetailViewProps {
  postId: number;
  onBack: () => void;
  onEdit?: () => void;
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

export function PostDetailView({ postId, onBack, onEdit }: PostDetailViewProps) {
  const { currentPost: d, comments, detailLoading, error, fetchPostDetail, fetchComments, fetchPosts } = useCommunityStore();
  const { user } = useAuth();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel } = useLoginDialog();
  const navigate = useNavigate();
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [aiTags, setAiTags] = useState<ParsedAiTags | null>(null);

  useEffect(() => {
    fetchPostDetail(postId);
    fetchComments(postId);
    communityApi.getAiTags(postId).then(setAiTags);
  }, [postId, fetchPostDetail, fetchComments]);

  // 작성자 차단 — 조용한 차단(상대에게 알리지 않음). 차단 직후 글/댓글을 다시 받아 톰스톤을 반영한다.
  // 익명 글은 작성자 id 가 없어 게시글 id 로 차단한다(서버가 작성자를 찾고, 목록에는 익명 라벨만 남는다).
  const handleBlockAuthor = async () => {
    if (!d || (!d.author.id && !d.author.isAnonymous)) return;
    try {
      if (d.author.isAnonymous || !d.author.id) {
        await blockUserByContent({ contentType: "POST", contentId: postId });
        showBlockManageToast("이 작성자를 차단했습니다.", "작성자가 누구인지는 표시되지 않습니다.");
      } else {
        await blockUser({ targetUserId: d.author.id });
        showBlockManageToast(`${d.author.name}님을 차단했습니다.`);
      }
      // 상세·댓글·목록 모두 다시 받아 이 작성자의 콘텐츠가 톰스톤으로 바뀌도록 한다.
      await Promise.all([fetchPostDetail(postId), fetchComments(postId), fetchPosts()]);
    } catch (err) {
      // 본인 콘텐츠/운영자 차단 등 서버 검증 메시지는 그대로 보여준다.
      toast.error(err instanceof Error && err.message ? err.message : "차단 처리에 실패했습니다.");
    }
  };

  const handleDelete = async () => {
    try {
      await communityApi.deletePost(postId);
      setShowDeleteDialog(false);
      toast.success("게시글이 삭제되었습니다.");
      await fetchPosts();
      onBack();
    } catch {
      setShowDeleteDialog(false);
      toast.error("게시글 삭제에 실패했습니다.");
    }
  };

  const adminSetStatus = async (status: "PUBLISHED" | "HIDDEN" | "DELETED") => {
    try {
      await communityApi.adminUpdatePostStatus(postId, status, "게시판 운영자 모드 즉시 조치");
      toast.success(status === "PUBLISHED" ? "게시글을 복원했습니다." : status === "HIDDEN" ? "게시글을 숨겼습니다." : "게시글을 삭제 처리했습니다.");
      await Promise.all([fetchPostDetail(postId), fetchPosts()]);
      if (status === "DELETED") onBack();
    } catch {
      toast.error("운영자 조치에 실패했습니다.");
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

  // 차단한 작성자의 글 — 톰스톤만 렌더하고 "한 번 보기" 없이 유지한다(조용한 차단).
  if (d.blocked) {
    return (
      <div className="ct-page ct-detail">
        <button className="ct-detail__back" onClick={onBack}>
          <ArrowLeft /> 커뮤니티 목록
        </button>
        <p style={{ textAlign: "center", color: "var(--muted-foreground)", padding: "48px 0", fontStyle: "italic" }}>
          차단한 사용자의 게시글입니다.
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
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", justifyContent: "flex-end" }}>
          {user && d && user.id === d.author.id && (
            <>
              <button className="av-btn" onClick={onEdit}>
                <Pencil /> 수정
              </button>
              <button
                className="av-btn"
                style={{ color: "var(--av-red, #dc2626)" }}
                onClick={() => setShowDeleteDialog(true)}
              >
                <Trash2 /> 삭제
              </button>
            </>
          )}
          {user && (user.role === "ADMIN" || user.role === "SUPER_ADMIN") && (
            <>
              <button className="av-btn" onClick={() => void adminSetStatus("HIDDEN")}>숨김</button>
              <button className="av-btn" onClick={() => void adminSetStatus("PUBLISHED")}>복원</button>
              <button className="av-btn" style={{ color: "var(--av-red, #dc2626)" }} onClick={() => void adminSetStatus("DELETED")}>운영 삭제</button>
            </>
          )}
        </div>
        {/* 작성자 메뉴 — 익명 글도 노출: 게시글 id 로 차단(서버가 작성자를 알므로 동작, 익명성 유지) */}
        {d && (d.author.isAnonymous || (!!d.author.id && (!user || user.id !== d.author.id))) && (
          <button
            className="av-btn"
            style={{ color: "var(--av-red, #dc2626)" }}
            onClick={() => requireAuth(() => void handleBlockAuthor())}
            title={
              d.author.isAnonymous
                ? "작성자가 누구인지는 표시되지 않습니다. 차단 사실은 상대에게 알려지지 않습니다."
                : "차단 사실은 상대에게 알려지지 않습니다."
            }
          >
            <UserX /> {d.author.isAnonymous ? "이 작성자 차단" : "이 사용자 차단"}
          </button>
        )}
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
            {/* 익명/차단 톰스톤 글은 작성자 정보가 비어 있을 수 있다 */}
            <AvatarFallback className="bg-muted text-sm">{(d.author?.name ?? "익")[0]}</AvatarFallback>
          </Avatar>
          <div className="ct-detail__who">
            {/* 비익명 작성자 이름 클릭 → 프로필 활동 탭(공개범위·차단은 서버가 검사) */}
            <div
              className={`ct-detail__name ${!d.author?.isAnonymous && d.author?.id ? "ct-author-link" : ""}`}
              onClick={() => {
                if (!d.author?.isAnonymous && d.author?.id) {
                  navigate(`/community/users/${d.author.id}/activity`);
                }
              }}
              title={!d.author?.isAnonymous && d.author?.id ? "작성자의 활동 보기" : undefined}
            >
              {d.author?.name ?? "익명"}
            </div>
            <div className="ct-detail__sub">
              <span><Clock />{relTime(d.createdAt)}</span>
              <span><Eye />조회 {d.stats.viewCount.toLocaleString()}</span>
            </div>
          </div>
        </div>
      </div>

      <hr style={{ border: "none", borderTop: "1px solid var(--border)", margin: "20px 0" }} />

      {/* Interview meta card — 메타에 회사/직무가 없으면 게시글 필드로 폴백(부분 응답 크래시 방지) */}
      {isInterview && iv && (
        <div className="ct-imeta">
          <div className="ct-imeta__top">
            <Avatar className="w-10 h-10">
              <AvatarFallback className="text-sm font-bold">{(iv.companyName ?? d.companyName ?? "-")[0]}</AvatarFallback>
            </Avatar>
            <div>
              <div className="ct-imeta__co">{iv.companyName ?? d.companyName ?? "-"}</div>
              <div className="ct-imeta__pos">{iv.jobRole ?? d.jobRole ?? ""}</div>
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

      {/* Tags */}
      {d.tags?.length > 0 && (
        <div className="ct-detail__taglist">
          {d.tags.map((tag) => (
            <span key={tag} className="ct-detail__tag">{tag}</span>
          ))}
        </div>
      )}

      {/* Body */}
      <div className="ct-prose">{renderMarkdown(d.content)}</div>

      {/* AI 추천 태그 (자동 적용 안 된 경우) */}
      {aiTags && !aiTags.applied && aiTags.tags.length > 0 && (
        <div className="ct-ai-suggest">
          <div className="ct-ai-suggest__h">
            <Sparkles /> AI 추천 태그
          </div>
          <div className="ct-ai-suggest__tags">
            {aiTags.tags.map((tag) => (
              <span key={tag} className="ct-detail__tag ct-detail__tag--ai">{tag}</span>
            ))}
          </div>
        </div>
      )}

      {/* Action bar — 추천/비추천 · 좋아요/싫어요 · 즐겨찾기 · 스크랩 · 구독 + 익명 반응 옵션 */}
      <ReactionButtons post={d} />

      {/* Comments */}
      <CommentSection postId={d.id} comments={comments} />

      {/* 로그인 유도 다이얼로그 (작성자 차단은 로그인 필요) */}
      {showLoginDialog && (
        <ConfirmDialog
          variant="info"
          icon={<Lock />}
          title="로그인이 필요해요"
          description="이 기능은 로그인 후에 이용할 수 있어요. 30초면 시작할 수 있습니다."
          confirmLabel="로그인하기"
          cancelLabel="둘러보기"
          onConfirm={onLoginConfirm}
          onCancel={onLoginCancel}
        />
      )}

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
