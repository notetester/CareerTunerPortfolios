package com.careertuner.correction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;

class CorrectionAiUsageLogServiceTest {

    private final ApplicationCaseMapper mapper = org.mockito.Mockito.mock(ApplicationCaseMapper.class);
    private final CorrectionAiUsageLogService service = new CorrectionAiUsageLogService(mapper);

    @Test
    void successLogStartsWithZeroCreditUntilChargeIsFinalized() {
        doAnswer(invocation -> {
            AiUsageLog log = invocation.getArgument(0);
            log.setId(501L);
            return null;
        }).when(mapper).insertAiUsageLog(any());

        Long id = service.recordSuccess(1L, 10L, "CORRECTION_SELF_INTRO", new Usage("model", 100, 50, 150));

        assertThat(id).isEqualTo(501L);
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        org.mockito.Mockito.verify(mapper).insertAiUsageLog(captor.capture());
        assertThat(captor.getValue()).satisfies(log -> {
            assertThat(log.getStatus()).isEqualTo("SUCCESS");
            assertThat(log.getInputTokens()).isEqualTo(100);
            assertThat(log.getOutputTokens()).isEqualTo(50);
            assertThat(log.getTokenUsage()).isEqualTo(150);
            assertThat(log.getCreditUsed()).isZero();
        });
    }

    @Test
    void missingUsageCannotCreateSuccessLog() {
        assertThatThrownBy(() -> service.recordSuccess(1L, null, "CORRECTION_SELF_INTRO", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
