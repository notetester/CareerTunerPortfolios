package com.careertuner.ai.autoprep.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.ai.autoprep.PrepProgress;
import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.correction.dto.CorrectionCreateRequest;
import com.careertuner.correction.service.CorrectionService;

/**
 * 챗봇 autoprep(무과금) 첨삭 경로의 멱등키 회귀 테스트.
 *
 * <p>챗봇 "다시 시도"가 실제 재전송으로 바뀐 뒤(2c279703), requestKey 가 null 이면 재시도마다
 * AI 를 재호출하고 {@code correction_request} 중복 행이 쌓였다. 같은 입력이면 같은 키가 나가는지 고정한다.
 */
class WritePrepHandlerIdempotencyTest {

    private static PrepStepContext contextOf(Long caseId, String coverLetter) {
        return new PrepStepContext(7L, caseId, null, coverLetter, List.of(), Map.of());
    }

    private static CorrectionCreateRequest captureRequest(CorrectionService correctionService) {
        ArgumentCaptor<CorrectionCreateRequest> captor = ArgumentCaptor.forClass(CorrectionCreateRequest.class);
        verify(correctionService).createUnchargedForAutoPrep(eq(7L), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("autoprep 첨삭은 requestKey 를 비우지 않는다 — 형식 규칙도 만족")
    void sendsNonNullRequestKeyMatchingTheFormatRule() {
        CorrectionService correctionService = mock(CorrectionService.class);
        new WritePrepHandler(correctionService).handle(contextOf(42L, "자소서 원문입니다."), mock(PrepProgress.class));

        String requestKey = captureRequest(correctionService).requestKey();

        assertThat(requestKey).isNotBlank();
        assertThat(requestKey).hasSizeLessThanOrEqualTo(120);
        assertThat(requestKey).matches("[A-Za-z0-9:_-]+");
    }

    @Test
    @DisplayName("같은 지원건 + 같은 원문이면 재시도해도 같은 키 — 리플레이로 흡수된다")
    void sameCaseAndSameTextProduceTheSameKeyAcrossRetries() {
        CorrectionService first = mock(CorrectionService.class);
        CorrectionService second = mock(CorrectionService.class);

        new WritePrepHandler(first).handle(contextOf(42L, "동일한 원문"), mock(PrepProgress.class));
        new WritePrepHandler(second).handle(contextOf(42L, "동일한 원문"), mock(PrepProgress.class));

        // null == null 도 "같다"이므로 non-null 을 함께 못 박는다.
        assertThat(captureRequest(first).requestKey())
                .isNotBlank()
                .isEqualTo(captureRequest(second).requestKey());
    }

    @Test
    @DisplayName("원문이 달라지면 키도 달라진다 — 수정 후 재실행은 새로 교정")
    void differentTextProducesDifferentKey() {
        CorrectionService first = mock(CorrectionService.class);
        CorrectionService second = mock(CorrectionService.class);

        new WritePrepHandler(first).handle(contextOf(42L, "원문 A"), mock(PrepProgress.class));
        new WritePrepHandler(second).handle(contextOf(42L, "원문 B"), mock(PrepProgress.class));

        assertThat(captureRequest(first).requestKey())
                .isNotEqualTo(captureRequest(second).requestKey());
    }

    @Test
    @DisplayName("지원건이 다르면 같은 원문이라도 키가 달라진다")
    void differentApplicationCaseProducesDifferentKey() {
        CorrectionService first = mock(CorrectionService.class);
        CorrectionService second = mock(CorrectionService.class);

        new WritePrepHandler(first).handle(contextOf(42L, "같은 원문"), mock(PrepProgress.class));
        new WritePrepHandler(second).handle(contextOf(43L, "같은 원문"), mock(PrepProgress.class));

        assertThat(captureRequest(first).requestKey())
                .isNotEqualTo(captureRequest(second).requestKey());
    }

    @Test
    @DisplayName("지원건이 없어도(null) 키가 생성된다")
    void nullApplicationCaseStillProducesAValidKey() {
        CorrectionService correctionService = mock(CorrectionService.class);
        new WritePrepHandler(correctionService).handle(contextOf(null, "원문"), mock(PrepProgress.class));

        assertThat(captureRequest(correctionService).requestKey()).matches("[A-Za-z0-9:_-]+");
    }

    @Test
    @DisplayName("원문이 없으면 첨삭을 건너뛴다 — 기존 동작 유지")
    void skipsWhenNoOriginalText() {
        CorrectionService correctionService = mock(CorrectionService.class);
        var result = new WritePrepHandler(correctionService)
                .handle(contextOf(42L, "   "), mock(PrepProgress.class));

        verify(correctionService, times(0)).createUnchargedForAutoPrep(any(), any());
        assertThat(result).isNotNull();
    }
}
