package com.careertuner.correction.ai;

import java.util.List;

import org.springframework.stereotype.Service;

import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;

/**
 * 첨삭 AI 폴백 체인의 <b>최종 결정론 안전망</b>(자체→Claude→OpenAI 가 모두 실패했을 때).
 *
 * <p>C 적합도의 {@code MockFitAnalysisAiService} 와 같은 역할 — 외부 호출 없이 결정론으로 안전한
 * {@link CorrectionPayload} 를 만들어 <b>절대 예외를 던지지 않는다</b>. 원문을 그대로 유지(손실 없음)하고
 * AI 미적용을 안내한다. 이 tier 가 없으면 OpenAI 실패 시 예외가 화면까지 전파돼 첨삭 화면이 깨진다.
 */
@Service
public class MockCorrectionProvider implements CorrectionAiProvider {

    @Override
    public CorrectionPayload correct(CorrectionCommand command) {
        String original = command == null || command.originalText() == null ? "" : command.originalText();
        return new CorrectionPayload(
                original,
                "AI 첨삭을 일시적으로 사용할 수 없어 원문을 그대로 유지했습니다. 잠시 후 다시 시도해 주세요.",
                List.of(),
                List.of(),
                List.of(),
                new Usage("mock", 0, 0, 0));
    }
}
