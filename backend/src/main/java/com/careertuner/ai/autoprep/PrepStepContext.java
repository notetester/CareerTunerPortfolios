package com.careertuner.ai.autoprep;

import java.util.Map;

/**
 * 핸들러 실행 컨텍스트. 오케스트레이터가 각 단계에 전달한다.
 * prior 에는 앞 단계 결과를 key 별로 누적해(다음 단계가 참조 가능) 넘긴다.
 */
public record PrepStepContext(
    Long userId,
    Long applicationCaseId,
    PrepSlots slots,
    String coverLetterText,
    Map<String, Object> prior
) {
}
