import { useEffect, useState } from "react";
import { Bot, User, X, LoaderCircle, MessageSquare } from "lucide-react";
import { ApiError } from "@/app/lib/api";
import { getUnansweredConversation } from "../api/adminChatbotPanelApi";
import type { UnansweredConversation } from "../types/adminChatbotPanel";

interface ConversationDrillProps {
  /** 대표 군집 id. */
  clusterId: number;
  onClose: () => void;
}

/** role 이 user 계열이면 사용자 말풍선, 아니면 봇. */
function isUser(role: string): boolean {
  const r = (role || "").toLowerCase();
  return r === "user" || r === "human";
}

/**
 * 발생 대화 드릴 모달 — "이 질문이 나온 대화 보기".
 * question 원문 + fallbackMessage + contextTurns(말풍선). 턴이 비면 안내 문구.
 */
export default function ConversationDrill({ clusterId, onClose }: ConversationDrillProps) {
  const [data, setData] = useState<UnansweredConversation | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    getUnansweredConversation(clusterId)
      .then((d) => { if (alive) setData(d); })
      .catch((e) => { if (alive) setError(e instanceof ApiError ? e.message : "대화를 불러오지 못했습니다."); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [clusterId]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) { if (e.key === "Escape") onClose(); }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="ais-modal" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="ais-modal__box" onClick={(e) => e.stopPropagation()}>
        <div className="ais-modal__h">
          <span className="ais-modal__t"><MessageSquare />이 질문이 나온 대화</span>
          <button className="ais-modal__x" onClick={onClose} aria-label="닫기"><X /></button>
        </div>

        <div className="ais-modal__body">
          {loading ? (
            <div className="ais-genwait">
              <LoaderCircle className="ais-spin" />
              <span>대화를 불러오는 중…</span>
            </div>
          ) : error ? (
            <div className="ais-empty">{error}</div>
          ) : data ? (
            <>
              <div className="ais-conv__meta">
                {data.conversationId != null
                  ? <>대화 ID <b className="num">#{data.conversationId}</b></>
                  : <>연결된 대화 메모리가 없습니다.</>}
              </div>

              {/* 주변 맥락(있을 때만). 부분 응답으로 필드 누락 시 undefined → [] 정규화(빈 상태 처리 유지). */}
              {(data.contextTurns ?? []).length > 0 ? (
                <div className="ais-conv">
                  {(data.contextTurns ?? []).map((t, i) => {
                    const user = isUser(t.role);
                    return (
                      <div className={`ais-msg${user ? " me" : ""}`} key={i}>
                        <span className="ais-msg__av">{user ? <User /> : <Bot />}</span>
                        <div className="ais-msg__body">{t.text}</div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="ais-conv__none">이 대화의 추가 맥락은 없습니다.</div>
              )}

              {/* 핵심: 질문 원문 + 폴백 */}
              <div className="ais-conv__core">
                <div className="ais-msg me">
                  <span className="ais-msg__av"><User /></span>
                  <div className="ais-msg__body">{data.question}</div>
                </div>
                <div className="ais-msg">
                  <span className="ais-msg__av"><Bot /></span>
                  <div className="ais-msg__body ais-msg__body--fallback">{data.fallbackMessage}</div>
                </div>
              </div>
            </>
          ) : null}
        </div>
      </div>
    </div>
  );
}
