package com.careertuner.ai.chat;

import java.util.List;

import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.chat.ChatResponse.SiteLink;

/**
 * 챗봇 에이전트 응답. conversationId 를 함께 내려 클라이언트가 다음 턴에 재사용한다.
 *
 * @param route           통합 라우팅 진단 라벨(①/③/확인반환/NAV/③(유지)/이탈 등). 프런트 비필수 메타.
 * @param intake          ③ 인테이크 입구로 보낸 턴에서만 non-null(그 외 null). ready/nextAsk + 칩 렌더용 후보.
 * @param inOrchestration 이 턴 이후 위젯이 오케스트레이터(인테이크) 모드를 유지해야 하는지의 단일 신호.
 *                        프런트는 유추하지 말고 이 값으로 모드 배너·sticky 입력을 토글한다.
 * @param summaryChip     이번 턴에 글이 2개 이상 제시됐을 때만 non-null. "추천 후기 N개 요약" 칩 1개로,
 *                        프런트가 누르면 postIds 를 묶음 요약 엔드포인트로 보낸다. 그 외 모든 응답은 null.
 */
public record ChatAskResponse(
        Long conversationId,
        String message,
        List<SiteLink> links,
        List<String> quickReplies,
        String route,
        IntakeStep intake,
        boolean inOrchestration,
        SummaryChip summaryChip
) {
    /**
     * ③ 인테이크 한 턴 결과 메타(ready/nextAsk/조립된 AutoPrepRequest + 칩 후보).
     * candidates 는 nextAsk="CASE", modes 는 nextAsk="MODE" 일 때 서버가 결정적으로 채운다.
     */
    public record IntakeStep(
            boolean ready,
            String nextAsk,
            AutoPrepRequest autoPrepRequest,
            List<CaseCandidate> candidates,
            List<ModeOption> modes
    ) {}

    /** 지원 건 후보(카드형 칩 렌더용 최소 필드). */
    public record CaseCandidate(Long id, String companyName, String jobTitle, String status) {}

    /** 면접 모드 선택지(code=백엔드 mode 값, label=표시명). */
    public record ModeOption(String code, String label) {}

    /** 추천 후기 묶음 요약 칩(label=표시 문구, postIds=요약 대상 글 id 들, 최대 3개). */
    public record SummaryChip(String label, List<Long> postIds) {}
}
