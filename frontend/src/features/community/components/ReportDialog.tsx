import { useState } from "react";
import { X, Flag, CheckCircle2 } from "lucide-react";
import * as communityApi from "../api/communityApi";

const REASONS = [
  "스팸/광고",
  "욕설/혐오 표현",
  "허위 정보",
  "개인정보 노출",
  "기타",
] as const;

const REASON_MAP: Record<string, string> = {
  "스팸/광고": "SPAM",
  "욕설/혐오 표현": "ABUSE",
  "허위 정보": "FALSE_INFO",
  "개인정보 노출": "PRIVACY",
  "기타": "OTHER",
};

interface ReportDialogProps {
  targetType: "POST" | "COMMENT";
  targetId: number;
  target: "게시글" | "댓글";
  onClose: () => void;
}

export function ReportDialog({ targetType, targetId, target, onClose }: ReportDialogProps) {
  const [reason, setReason] = useState("");
  const [detail, setDetail] = useState("");
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const isEtc = reason === "기타";
  const canSubmit = reason && (!isEtc || detail.trim().length >= 5) && !submitting;

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    try {
      await communityApi.createReport(
        targetType,
        targetId,
        REASON_MAP[reason] ?? "OTHER",
        detail || undefined,
      );
      setDone(true);
    } catch {
      setSubmitting(false);
      const { toast: t } = await import("@/features/notification/components/toast");
      t.error("신고 접수에 실패했습니다. 다시 시도해주세요.");
    }
  };

  if (done) {
    return (
      <div className="ct-modal__overlay" onClick={onClose}>
        <div className="ct-modal" onClick={(e) => e.stopPropagation()}>
          <div className="ct-modal__done">
            <div className="ct-modal__doneic"><CheckCircle2 /></div>
            <h3>신고가 접수되었습니다</h3>
            <p>검토 후 커뮤니티 가이드라인에 따라 조치할게요.<br />허위 신고가 반복되면 이용이 제한될 수 있습니다.</p>
            <button className="ct-act" onClick={onClose}>닫기</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="ct-modal__overlay" onClick={onClose}>
      <div className="ct-modal" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="ct-modal__head">
          <div className="ct-modal__headic"><Flag /></div>
          <h2>신고하기</h2>
          <button className="ct-modal__close" onClick={onClose}><X /></button>
        </div>

        {/* Body */}
        <div className="ct-modal__body">
          <p className="ct-modal__desc">
            이 {target}을(를) 신고하는 이유를 선택해주세요. 신고는 익명으로 처리됩니다.
          </p>

          <div className="ct-rgroup">
            {REASONS.map((r) => (
              <label key={r} className={`ct-rgroup__row ${reason === r ? "is-on" : ""}`}>
                <span className={`ct-radio ${reason === r ? "is-on" : ""}`}>
                  {reason === r && <span className="ct-radio__dot" />}
                </span>
                <input
                  type="radio"
                  name="report-reason"
                  value={r}
                  checked={reason === r}
                  onChange={() => setReason(r)}
                  hidden
                />
                {r}
              </label>
            ))}
          </div>

          <div className="ct-modal__field">
            <label className="ct-modal__label">
              상세 내용 {isEtc ? <span className="ct-modal__req">(필수)</span> : <span className="ct-modal__opt">(선택)</span>}
            </label>
            <textarea
              className="ct-modal__textarea"
              placeholder="구체적인 상황을 알려주시면 검토에 도움이 됩니다."
              value={detail}
              onChange={(e) => setDetail(e.target.value.slice(0, 500))}
              rows={3}
              maxLength={500}
            />
            <div className="ct-modal__count">{detail.length} / 500</div>
          </div>
        </div>

        {/* Footer */}
        <div className="ct-modal__foot">
          <button className="ct-act" onClick={onClose}>취소</button>
          <button
            className="ct-modal__submit"
            disabled={!canSubmit}
            onClick={handleSubmit}
          >
            {submitting ? "접수 중..." : "신고 접수"}
          </button>
        </div>
      </div>
    </div>
  );
}
