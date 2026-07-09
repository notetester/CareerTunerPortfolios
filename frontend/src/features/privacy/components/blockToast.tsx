// 차단 직후 확인 토스트 — "차단 관리에서 세부 조정" 링크 포함 (설계문서 §4-3 차단 진입점 공통).
// toast 는 라우터 밖에 자체 마운트되므로 Link 대신 <a> 를 쓴다(설정 탭 쿼리로 진입).
import { toast } from "@/features/notification/components/toast";

export function showBlockManageToast(message: string, note?: string) {
  toast.success(
    <span>
      {message}
      {note ? <span style={{ display: "block", fontSize: 12, opacity: 0.85 }}>{note}</span> : null}
      <a
        href="/settings?tab=blocks"
        style={{ display: "block", marginTop: 2, fontWeight: 600, textDecoration: "underline" }}
      >
        차단 관리에서 세부 조정
      </a>
    </span>,
    { duration: 6000 },
  );
}
