import { useState } from "react";
import { useNavigate } from "react-router";
import { ArrowLeft, Clock, User, FileWarning, ExternalLink, Trash2, VenetianMask } from "lucide-react";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import { toast } from "@/features/notification/components/toast";
import * as communityApi from "../api/communityApi";
import { relTime } from "@/features/notification/types/notification";
import type { ScrapItem } from "../types/community";

interface ScrapDetailViewProps {
  scrap: ScrapItem;
  onBack: () => void;
  /** 삭제 완료 후 목록 갱신용 */
  onDeleted: () => void;
}

/**
 * 스크랩 상세 — 스크랩 시점의 스냅샷(제목/본문/작성자 표시명/카테고리)을 보여준다.
 * 원본이 수정·삭제돼도 이 화면은 그대로 열람 가능하고, 원본이 살아 있으면 원본으로 이동할 수 있다.
 */
export function ScrapDetailView({ scrap, onBack, onDeleted }: ScrapDetailViewProps) {
  const navigate = useNavigate();
  const [showDelete, setShowDelete] = useState(false);

  const handleDelete = async () => {
    try {
      await communityApi.deleteScrap(scrap.id);
      setShowDelete(false);
      toast.success("스크랩을 삭제했습니다.");
      onDeleted();
    } catch {
      setShowDelete(false);
      toast.error("스크랩 삭제에 실패했습니다.");
    }
  };

  return (
    <div className="ct-page ct-detail">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <button className="ct-detail__back" onClick={onBack}>
          <ArrowLeft /> 스크랩 목록
        </button>
        <div style={{ display: "flex", gap: 8 }}>
          {scrap.originAvailable && scrap.postId != null && (
            <button className="av-btn" onClick={() => navigate(`/community/posts/${scrap.postId}`)}>
              <ExternalLink /> 원본 글 보기
            </button>
          )}
          <button
            className="av-btn"
            style={{ color: "var(--av-red, #dc2626)" }}
            onClick={() => setShowDelete(true)}
          >
            <Trash2 /> 스크랩 삭제
          </button>
        </div>
      </div>

      <div className="ct-detail__head">
        <div className="ct-detail__tags">
          <span className="ct-badge">{scrap.category}</span>
          {!scrap.originAvailable && (
            <span className="ct-badge" style={{ background: "var(--danger-50)", color: "var(--destructive)" }}>
              <FileWarning style={{ width: 13, height: 13, verticalAlign: "-2px" }} /> 원본이 삭제된 글
            </span>
          )}
          {scrap.anonymous && (
            <span className="ct-anon-chip">
              <VenetianMask style={{ width: 12, height: 12 }} /> 익명 스크랩
            </span>
          )}
        </div>
        <h1 className="ct-detail__title">{scrap.title}</h1>
        <div className="ct-activity-item__meta" style={{ marginTop: 8 }}>
          <span><User style={{ width: 13, height: 13, verticalAlign: "-2px" }} /> {scrap.authorLabel}</span>
          <span><Clock style={{ width: 13, height: 13, verticalAlign: "-2px" }} /> {relTime(scrap.scrappedAt)} 스크랩</span>
        </div>
      </div>

      <hr style={{ border: "none", borderTop: "1px solid var(--border)", margin: "20px 0" }} />

      <p style={{ fontSize: 13, color: "var(--muted-foreground)", marginBottom: 14 }}>
        스크랩 시점의 내용입니다. 원본 글이 수정되거나 삭제되어도 이 내용은 유지됩니다.
      </p>

      {/* 스냅샷 본문 — 원문 그대로(마크다운 렌더 대신 보존 우선) */}
      <div className="ct-prose" style={{ whiteSpace: "pre-wrap" }}>{scrap.content}</div>

      {showDelete && (
        <ConfirmDialog
          variant="danger"
          icon={<Trash2 />}
          title="이 스크랩을 삭제할까요?"
          description="스냅샷이 함께 삭제되며 되돌릴 수 없어요. 원본 글에는 영향이 없습니다."
          confirmLabel="삭제"
          cancelLabel="취소"
          onConfirm={handleDelete}
          onCancel={() => setShowDelete(false)}
        />
      )}
    </div>
  );
}
