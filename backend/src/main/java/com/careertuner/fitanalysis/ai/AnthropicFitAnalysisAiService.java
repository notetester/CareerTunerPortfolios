package com.careertuner.fitanalysis.ai;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAnthropicClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.fitanalysis.ai.prompt.FitAnalysisPromptCatalog;

/**
 * 적합도 분석의 Claude(Haiku) 단계 — 폴백 디스패처의 1차 폴백 provider.
 *
 * <p><b>뉴로-심볼릭(OSS 경로와 동일 계약)</b>: 판단값(fitScore/matched/missing/applyDecision 등)은 서버
 * 규칙엔진({@link MockFitAnalysisAiService}) skeleton 이 소유하고, Claude 는 설명 JSON 만 생성한다
 * ({@code FIT_EXPLAIN_SYSTEM_PROMPT}). 조립·grounding 검사는 {@link FitExplainAssembler} 공유.
 *
 * <p>키가 없거나 호출/검증(grounding 포함)이 실패하면 예외를 던지고, 상위
 * {@link FallbackFitAnalysisAiService} 가 OpenAI 단계로 폴백한다(이 클래스는 자체 mock 폴백을 두지 않는다).
 */
@Service
public class AnthropicFitAnalysisAiService implements FitAnalysisAiService {

    private final CareerAnalysisAnthropicClient anthropicClient;
    private final MockFitAnalysisAiService ruleEngine;
    private final FitExplainAssembler assembler;

    public AnthropicFitAnalysisAiService(CareerAnalysisAnthropicClient anthropicClient,
                                         MockFitAnalysisAiService ruleEngine,
                                         FitExplainAssembler assembler) {
        this.anthropicClient = anthropicClient;
        this.ruleEngine = ruleEngine;
        this.assembler = assembler;
    }

    public boolean configured() {
        return anthropicClient.configured();
    }

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        // 1) 판단값은 규칙엔진이 결정론으로 계산(서버 권위).
        FitAnalysisAiResult skeleton = ruleEngine.generate(command);
        // 2) Claude 는 skeleton 값을 입력으로 받아 설명만 생성.
        StructuredResponse response = anthropicClient.request(
                FitExplainAssembler.EXPLAIN_SCHEMA_NAME,
                assembler.explainSchema(),
                FitAnalysisPromptCatalog.FIT_EXPLAIN_SYSTEM_PROMPT,
                assembler.explainUserPrompt(command, skeleton));
        // 3) 검증(fitSummary/grounding) + 병합. 위반 시 예외 → 상위 디스패처가 OpenAI 로 폴백.
        return assembler.assemble(command, skeleton, response.payload(), response.usage());
    }
}
