package com.careertuner.ai.autoprep;

import java.util.List;
import java.util.Map;

/**
 * 핸들러 실행 컨텍스트. 오케스트레이터가 각 단계에 전달한다.
 * prior 에는 앞 단계 결과를 key 별로 누적해(다음 단계가 참조 가능) 넘긴다.
 * attachments 에는 플랜 게이팅을 통과한 첨부 파일(텍스트형은 본문 추출됨)이 담긴다.
 */
public record PrepStepContext(
    Long userId,
    Long applicationCaseId,
    PrepSlots slots,
    String coverLetterText,
    List<PrepAttachment> attachments,
    Map<String, Object> prior,
    AutoPrepCancellation cancellation
) {
    /** 기존 핸들러 단위 테스트·직접 호출은 취소되지 않는 독립 실행으로 호환한다. */
    public PrepStepContext(Long userId,
                           Long applicationCaseId,
                           PrepSlots slots,
                           String coverLetterText,
                           List<PrepAttachment> attachments,
                           Map<String, Object> prior) {
        this(userId, applicationCaseId, slots, coverLetterText, attachments, prior,
                new AutoPrepCancellation());
    }

    /** 유료 제공자 호출 또는 DB 변경 직전에 호출하는 협력적 취소 경계. */
    public void checkActive() {
        cancellation.checkActive();
    }
}
