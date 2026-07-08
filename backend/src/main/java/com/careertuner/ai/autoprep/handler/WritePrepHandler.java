package com.careertuner.ai.autoprep.handler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
                        "SELF_INTRO", context.applicationCaseId(), original, null, null, null, null,
                        idempotencyKey(context.applicationCaseId(), original)));
        long ms = (System.nanoTime() - start) / 1_000_000;
        return PrepStepResult.done("WRITE", "자소서 교정 완료", result, ms);
    }

    /**
     * 무과금 autoprep 경로의 멱등키. 챗봇의 "다시 시도"는 실제 재전송이라(2c279703) 키가 없으면
     * 재시도마다 AI 를 다시 태우고 {@code correction_request} 중복 행이 쌓인다.
     *
     * <p>같은 지원건 + 같은 원문이면 같은 키 → 기존 결과를 리플레이한다. 원문을 고치면 해시가 달라져
     * 새로 교정한다. 형식은 {@code CorrectionCreateRequest#requestKey} 규칙({@code [A-Za-z0-9:_-]+}, ≤120자)을 지킨다.
     */
    private static String idempotencyKey(Long applicationCaseId, String originalText) {
        return "autoprep:write:" + (applicationCaseId == null ? "na" : applicationCaseId)
                + ":" + sha256Prefix(originalText);
    }

    /** 원문 해시 앞 32 hex. 충돌 확률은 무시 가능하고 키 길이를 상한 안에 묶는다. */
    private static String sha256Prefix(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                hex.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 미지원 JVM", ex);
        }
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
