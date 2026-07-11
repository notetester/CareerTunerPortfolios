import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import {
  ArrowLeft, Link2, Ellipsis, Pencil, Trash2, UserX, EyeOff, RotateCcw, Lock, Sparkles,
  Bell, BellRing, Flag,
} from "lucide-react";
// 개인 차단 진입점 — 익명 글은 작성자 id 가 클라이언트에 없어 게시글 id 로 차단한다(익명성 유지).
import { blockUser, blockUserByContent } from "@/features/privacy/api/privacyApi";
import { showBlockManageToast } from "@/features/privacy/components/blockToast";
import { ReactionButtons } from "./ReactionButtons";
import { CommentSection } from "./CommentSection";
import { ReportDialog } from "./ReportDialog";
import { RxMenu, RxMenuItem } from "./RxMenu";
import { sanitizePostHtml, isHtmlContent } from "@/app/lib/postContent";
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

function DifficultyDots({ level }: { level: number }) {
  return (
    <span className="dv-dots" aria-label={`난이도 ${level}/5`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <i key={n} className={n <= level ? "" : "off"} />
      ))}
    </span>
  );
}

const RESULT_LABELS: Record<string, string> = {
  PASSED: "최종합격", FAILED: "불합격", PENDING: "대기중", UNKNOWN: "비공개",
};

// 블러 오버레이에 표시할 사유(카테고리 → 사용자용 문구). 없으면 기본 문구.
const IMG_BLUR_REASON: Record<string, string> = {
  ad: "광고로 분류된 이미지",
  spam: "스팸으로 분류된 이미지",
  pii: "개인정보가 포함될 수 있는 이미지",
  gross: "불쾌감을 줄 수 있는 이미지",
  abuse: "민감할 수 있는 이미지",
};

export function PostDetailView({ postId, onBack, onEdit }: PostDetailViewProps) {
  const { currentPost: d, comments, detailLoading, detailNotFound, detailError, fetchPostDetail, fetchComments, fetchPosts, togglePostSubscription } = useCommunityStore();
  const { user } = useAuth();
  const { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel } = useLoginDialog();
  const navigate = useNavigate();
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [showReport, setShowReport] = useState(false);
  const [subBusy, setSubBusy] = useState(false);
  const [aiTags, setAiTags] = useState<ParsedAiTags | null>(null);
  const contentRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    fetchPostDetail(postId);
    fetchComments(postId);
    communityApi.getAiTags(postId).then(setAiTags);
  }, [postId, fetchPostDetail, fetchComments]);

  // AI 이미지 검열에서 블러 대상으로 판정된 본문 이미지만 블러 + 사유 + 클릭하여 보기.
  // vision 판정이 불완전하므로 글은 숨기지 않고 소프트하게 가린다(백엔드 fail-open과 정합).
  const blurredKey = (d?.blurredImages ?? []).map((b) => `${b.url}:${b.category}`).join("|");
  useEffect(() => {
    const root = contentRef.current;
    if (!root) return;
    const reasons = new Map(
      (d?.blurredImages ?? []).filter((b) => b.url).map((b) => [b.url, b.category] as const),
    );
    if (reasons.size === 0) return;
    root.querySelectorAll("img").forEach((img) => {
      const src = img.getAttribute("src") ?? "";
      if (!reasons.has(src)) return;
      if (img.closest(".dv-imgwrap")) return; // 이미 감싼 이미지
      const reason = IMG_BLUR_REASON[reasons.get(src) ?? ""] ?? "민감할 수 있는 이미지";
      const wrap = document.createElement("span");
      wrap.className = "dv-imgwrap blurred";
      const hint = document.createElement("span");
      hint.className = "dv-imgwrap__hint";
      // reason 은 고정 매핑 문자열(사용자 입력 아님) — 안전
      hint.innerHTML = `${reason}<span>클릭하여 보기</span>`;
      img.parentNode?.insertBefore(wrap, img);
      wrap.appendChild(img);
      wrap.appendChild(hint);
      wrap.addEventListener("click", () => wrap.classList.add("revealed"), { once: true });
    });
    // d.content 가 바뀌면 React 가 innerHTML 을 다시 채워 래퍼가 사라지므로 재적용된다.
  }, [d?.content, blurredKey]);

  // 공유 — 백엔드 없이 클라이언트에서 처리. Web Share 지원 시 시스템 시트, 아니면 링크 복사.
  const handleShare = async () => {
    const url = `${window.location.origin}/community/posts/${postId}`;
    try {
      if (navigator.share) {
        await navigator.share({ title: d?.title ?? "커뮤니티 글", url });
      } else {
        await navigator.clipboard.writeText(url);
        toast.success("링크를 복사했어요.");
      }
    } catch {
      /* 사용자가 공유를 취소함 — 무시 */
    }
  };

  // 이 글 알림(watch) — 글 구독 토글(새 댓글 알림). 작성자 팔로우가 아니라 이 글 스레드 알림.
  const handleWatch = () => {
    requireAuth(async () => {
      if (subBusy) return;
      setSubBusy(true);
      try {
        const active = await togglePostSubscription(postId);
        toast.success(active ? "이 글을 구독합니다. 새 댓글이 달리면 알려드릴게요." : "글 구독을 해지했습니다.");
      } catch {
        toast.error("구독 처리에 실패했습니다.");
      } finally {
        setSubBusy(false);
      }
    });
  };

  // 작성자 차단 — 조용한 차단(상대에게 알리지 않음). 차단 직후 글/댓글을 다시 받아 톰스톤을 반영한다.
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
      await Promise.all([fetchPostDetail(postId), fetchComments(postId), fetchPosts()]);
    } catch (err) {
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
      <div className="cv-page">
        <div className="dv-wrap">
          <button className="dv-back" onClick={onBack}><ArrowLeft /> 커뮤니티로 돌아가기</button>
          <p className="av-empty">불러오는 중...</p>
        </div>
      </div>
    );
  }

  if (!d) {
    // 404 는 삭제·숨김·없는 글을 구분하지 않는다(백엔드가 모두 NOT_FOUND). 오래된 알림 링크가 주로 여기로 온다.
    return (
      <div className="cv-page">
        <div className="dv-wrap">
          <button className="dv-back" onClick={onBack}><ArrowLeft /> 커뮤니티로 돌아가기</button>
          <p className="av-empty">
            {detailNotFound ? "삭제되었거나 볼 수 없는 게시글입니다." : (detailError ?? "게시글을 불러올 수 없습니다.")}
          </p>
        </div>
      </div>
    );
  }

  // 차단한 작성자의 글 — 톰스톤만 렌더하고 "한 번 보기" 없이 유지한다(조용한 차단).
  if (d.blocked) {
    return (
      <div className="cv-page">
        <div className="dv-wrap">
          <button className="dv-back" onClick={onBack}><ArrowLeft /> 커뮤니티로 돌아가기</button>
          <p className="av-empty" style={{ fontStyle: "italic" }}>차단한 사용자의 게시글입니다.</p>
        </div>
      </div>
    );
  }

  const iv = d.interviewReview;
  const isInterview = !!iv;
  const resultLabel = iv?.resultStatus ? RESULT_LABELS[iv.resultStatus] : d.result;
  const resultColor = iv?.resultStatus === "PASSED" ? "var(--av-green)"
    : iv?.resultStatus === "FAILED" ? "var(--av-red)"
    : "var(--av-ink-3)";

  const canNameLink = !d.author?.isAnonymous && !!d.author?.id;
  // 익명 글은 author.id 가 마스킹돼 null 이라 id 비교로는 소유자를 못 가린다 → 서버가 내려준 mine 플래그로 판정.
  // (댓글의 mine 게이팅과 동형) — mine 이 없으면(구버전 응답) id 비교로 폴백.
  const isOwner = !!user && (d.mine ?? user.id === d.author.id);
  const isAdmin = !!user && (user.role === "ADMIN" || user.role === "SUPER_ADMIN");
  // 익명 글도 노출: 게시글 id 로 차단(서버가 작성자를 알므로 동작, 익명성 유지)
  const canBlock = d.author.isAnonymous || (!!d.author.id && (!user || user.id !== d.author.id));

  const metaCells: { k: string; v: React.ReactNode }[] = [];
  if (iv) {
    if (iv.interviewType) metaCells.push({ k: "면접 유형", v: iv.interviewType });
    if (iv.interviewDate) metaCells.push({ k: "면접일", v: <span className="num">{iv.interviewDate}</span> });
    if (iv.difficulty) metaCells.push({ k: "체감 난이도", v: <DifficultyDots level={iv.difficulty} /> });
  }

  return (
    <div className="cv-page">
      <div className="dv-wrap" data-screen-label="커뮤니티 상세">
        <button className="dv-back" onClick={onBack}><ArrowLeft /> 커뮤니티로 돌아가기</button>

        <header>
          <div className="dv-eyebrow">
            {d.categoryLabel}{d.isHot && <span className="hot">인기</span>}
          </div>
          <h1 className="dv-title">{d.title}</h1>
          <div className="dv-byline">
            <Avatar className="w-9 h-9 shrink-0">
              <AvatarFallback className="bg-muted text-sm">{(d.author?.name ?? "익")[0]}</AvatarFallback>
            </Avatar>
            <div className="dv-byline__who">
              <div
                className={"dv-byline__n" + (canNameLink ? " link" : "")}
                onClick={() => { if (canNameLink) navigate(`/community/users/${d.author.id}/activity`); }}
                title={canNameLink ? "작성자의 활동 보기" : undefined}
              >
                {d.author?.name ?? "익명"}
              </div>
              <div className="dv-byline__s num">
                {relTime(d.createdAt)} · 조회 {d.stats.viewCount.toLocaleString()} · 추천 {(d.stats.recommendCount ?? 0).toLocaleString()}
              </div>
            </div>
            <div className="dv-byline__r">
              {/* 이 글 알림(watch) — 글 구독(새 댓글 알림) */}
              <button
                className={"av-btn" + (d.subscribed ? " av-btn--ink" : "")}
                style={{ height: 30, width: 30, padding: 0, justifyContent: "center" }}
                onClick={handleWatch}
                disabled={subBusy}
                aria-pressed={!!d.subscribed}
                aria-label={d.subscribed ? "이 글 알림 켜짐" : "이 글 알림 받기"}
                data-tip={d.subscribed ? "새 댓글 알림 받는 중" : "이 글의 새 댓글 알림 받기"}
              >
                {d.subscribed ? <BellRing /> : <Bell />}
              </button>
              {/* 2차 액션 통합 메뉴 — 공유·신고·수정/삭제·운영·차단 */}
              <RxMenu
                align="right"
                width={260}
                label="게시글 메뉴"
                triggerClassName="av-btn"
                triggerStyle={{ height: 30, width: 30, padding: 0, justifyContent: "center" }}
                triggerContent={<Ellipsis />}
              >
                {(close) => (
                  <>
                    <RxMenuItem icon={<Link2 />} label="공유" desc="링크 복사 또는 공유하기" onClick={() => { close(); void handleShare(); }} />
                    {!isOwner && (
                      <RxMenuItem icon={<Flag />} label="신고" desc="커뮤니티 가이드라인 위반을 알려요" onClick={() => { close(); requireAuth(() => setShowReport(true)); }} />
                    )}
                    {isOwner && (
                      <>
                        <RxMenuItem icon={<Pencil />} label="수정" onClick={() => { close(); onEdit?.(); }} />
                        <RxMenuItem icon={<Trash2 />} label="삭제" danger onClick={() => { close(); setShowDeleteDialog(true); }} />
                      </>
                    )}
                    {isAdmin && (
                      <>
                        <RxMenuItem icon={<EyeOff />} label="숨김 처리" desc="운영자 조치 — 목록에서 숨겨요" onClick={() => { close(); void adminSetStatus("HIDDEN"); }} />
                        <RxMenuItem icon={<RotateCcw />} label="복원" onClick={() => { close(); void adminSetStatus("PUBLISHED"); }} />
                        <RxMenuItem icon={<Trash2 />} label="운영 삭제" danger onClick={() => { close(); void adminSetStatus("DELETED"); }} />
                      </>
                    )}
                    {canBlock && (
                      <RxMenuItem
                        icon={<UserX />}
                        label={d.author.isAnonymous ? "이 작성자 차단" : "이 사용자 차단"}
                        danger
                        desc="이 작성자의 글·댓글을 숨겨요 — 상대에게 알리지 않아요"
                        onClick={() => { close(); requireAuth(() => void handleBlockAuthor()); }}
                      />
                    )}
                  </>
                )}
              </RxMenu>
            </div>
          </div>
        </header>

        {/* 면접 메타 스트립 — 메타에 회사/직무가 없으면 게시글 필드로 폴백 */}
        {isInterview && iv && (
          <section className="av-panel dv-meta" aria-label="면접 정보">
            <div className="dv-meta__co">
              <span className="dv-meta__logo">{(iv.companyName ?? d.companyName ?? "-")[0]}</span>
              <div style={{ minWidth: 0 }}>
                <div className="dv-meta__name">{iv.companyName ?? d.companyName ?? "-"}</div>
                {(iv.jobRole ?? d.jobRole) && <div className="dv-meta__pos">{iv.jobRole ?? d.jobRole}</div>}
              </div>
              {resultLabel && (
                <span className="dv-meta__result" style={{ fontSize: 12, fontWeight: 700, color: resultColor }}>{resultLabel}</span>
              )}
            </div>
            {metaCells.length > 0 && (
              <div className="dv-meta__grid">
                {metaCells.map((cell) => (
                  <div className="dv-meta__cell" key={cell.k}>
                    <div className="dv-meta__k">{cell.k}</div>
                    <div className="dv-meta__v">{cell.v}</div>
                  </div>
                ))}
              </div>
            )}
          </section>
        )}

        {/* 본문 — HTML 글(TipTap)이면 sanitize 후 렌더, 기존 평문 글이면 줄바꿈 보존(무회귀) */}
        {isHtmlContent(d.content) ? (
          <article
            ref={contentRef}
            className="dv-prose"
            dangerouslySetInnerHTML={{ __html: sanitizePostHtml(d.content) }}
          />
        ) : (
          <article className="dv-prose" style={{ whiteSpace: "pre-wrap" }}>{d.content}</article>
        )}

        {/* 태그 */}
        {d.tags?.length > 0 && (
          <div className="dv-tags">
            {d.tags.map((tag) => <span key={tag} className="dv-tag">#{tag}</span>)}
          </div>
        )}

        {/* AI 추천 태그 (자동 적용 안 된 경우) */}
        {aiTags && !aiTags.applied && aiTags.tags.length > 0 && (
          <>
            <div className="dv-aihint"><Sparkles /> AI 추천 태그</div>
            <div className="dv-tags">
              {aiTags.tags.map((tag) => <span key={tag} className="dv-tag dv-tag--ai">#{tag}</span>)}
            </div>
          </>
        )}

        {/* 반응 바 */}
        <ReactionButtons post={d} />

        {/* 댓글 */}
        <CommentSection postId={d.id} comments={comments} />
      </div>

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

      {/* 신고 다이얼로그 (상단 ⋯ 메뉴에서 진입) */}
      {showReport && (
        <ReportDialog
          targetType="POST"
          targetId={d.id}
          target="게시글"
          onClose={() => setShowReport(false)}
        />
      )}

      {/* 삭제 확인 */}
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
