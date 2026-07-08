package com.careertuner.ai.autoprep.handler;

import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.PrepAttachment;
import com.careertuner.ai.autoprep.PrepProgress;
import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.ai.autoprep.PrepStepHandler;
import com.careertuner.ai.autoprep.PrepStepResult;
import com.careertuner.correction.dto.CorrectionCreateRequest;
import com.careertuner.correction.dto.CorrectionResponse;
import com.careertuner.correction.service.CorrectionService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 자소서 교정 (E). 자소서 원문을 AI로 교정한다(자체→폴백).
 * 원문은 coverLetterText 우선, 없으면 첨부 파일(텍스트형) 본문을 쓴다. 둘 다 없으면 건너뛴다.
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
    public PrepStepResult handle(PrepStepContext context, PrepProgress progress) {
        String original = resolveOriginal(context);
        if (original == null || original.isBlank()) {
            return PrepStepResult.skipped("WRITE", "자소서 원문이 없어 건너뜀 — 자소서를 입력하거나 첨부하면 교정합니다.");
        }
        long start = System.nanoTime();
        progress.substep("원문 분석", "자소서 문장 구조 파악");
        progress.substep("문장 교정", "AI 첨삭·근거 보강");
        CorrectionResponse result = correctionService.createUnchargedForAutoPrep(
                context.userId(),
                new CorrectionCreateRequest(
                        "SELF_INTRO", context.applicationCaseId(), original, null, null, null, null, null));
        long ms = (System.nanoTime() - start) / 1_000_000;
        return PrepStepResult.done("WRITE", "자소서 교정 완료", result, ms);
    }

    /** 원문 결정: 직접 입력한 coverLetterText 우선, 없으면 첨부 파일 중 텍스트형 첫 본문. */
    private String resolveOriginal(PrepStepContext context) {
        if (context.coverLetterText() != null && !context.coverLetterText().isBlank()) {
            return context.coverLetterText();
        }
        if (context.attachments() == null) {
            return null;
        }
        return context.attachments().stream()
                .filter(PrepAttachment::hasText)
                .map(PrepAttachment::text)
                .findFirst()
                .orElse(null);
    }
}
