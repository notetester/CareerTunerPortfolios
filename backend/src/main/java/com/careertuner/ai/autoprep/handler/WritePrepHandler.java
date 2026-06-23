package com.careertuner.ai.autoprep.handler;

import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.ai.autoprep.PrepStepHandler;
import com.careertuner.ai.autoprep.PrepStepResult;
import com.careertuner.correction.dto.CorrectionCreateRequest;
import com.careertuner.correction.dto.CorrectionResponse;
import com.careertuner.correction.service.CorrectionService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 자소서 교정 (E). 사용자가 제공한 자소서 원문을 AI로 교정한다(자체→폴백).
 * 자동 흐름에서 원문(coverLetterText)이 없으면 건너뛴다 — 자소서는 직접 입력해야 교정 가능.
 */
@Component
@RequiredArgsConstructor
public class WritePrepHandler implements PrepStepHandler {

    private final CorrectionService correctionService;

    @Override
    public String key() {
        return "WRITE";
    }

    @Override
    public PrepStepResult handle(PrepStepContext context) {
        String original = context.coverLetterText();
        if (original == null || original.isBlank()) {
            return PrepStepResult.skipped("WRITE", "자소서 원문이 없어 건너뜀 — 자소서를 입력하면 교정합니다.");
        }
        long start = System.nanoTime();
        CorrectionResponse result = correctionService.create(
                context.userId(),
                new CorrectionCreateRequest("SELF_INTRO", context.applicationCaseId(), original, null, null, null));
        long ms = (System.nanoTime() - start) / 1_000_000;
        return PrepStepResult.done("WRITE", "자소서 교정 완료", result, ms);
    }
}
