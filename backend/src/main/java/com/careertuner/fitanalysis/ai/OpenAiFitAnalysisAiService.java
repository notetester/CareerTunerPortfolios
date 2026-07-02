package com.careertuner.fitanalysis.ai;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.fitanalysis.ai.prompt.FitAnalysisPromptCatalog;

/**
 * 적합도 분석의 OpenAI 단계 — 폴백 체인의 마지막 실 LLM provider(내부 mock 폴백이 최종 안전망).
 *
 * <p><b>뉴로-심볼릭(OSS 경로와 동일 계약)</b>: 판단값(fitScore/matched/missing/applyDecision 등)은 서버
 * 규칙엔진({@link MockFitAnalysisAiService}) skeleton 이 소유하고, OpenAI 는 설명 JSON 만 생성한다
 * ({@code FIT_EXPLAIN_SYSTEM_PROMPT}). 조립·grounding 검사는 {@link FitExplainAssembler} 를 Claude 단계와 공유.
 *
 * <p>키가 없으면 결정적 mock(=규칙엔진 전체 결과)을 그대로 반환하고, 호출/검증(grounding 포함)이 실패하면
 * skeleton 값으로 FALLBACK 결과를 만들어 화면이 깨지지 않게 한다.
 */
@Service
public class OpenAiFitAnalysisAiService implements FitAnalysisAiService {

    private final CareerAnalysisOpenAiClient openAiClient;
    private final MockFitAnalysisAiService ruleEngine;
    private final FitExplainAssembler assembler;

    public OpenAiFitAnalysisAiService(CareerAnalysisOpenAiClient openAiClient,
                                      MockFitAnalysisAiService ruleEngine,
                                      FitExplainAssembler assembler) {
        this.openAiClient = openAiClient;
        this.ruleEngine = ruleEngine;
        this.assembler = assembler;
    }

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        if (!openAiClient.configured()) {
            return ruleEngine.generate(command);
        }

        // 1) 판단값은 규칙엔진이 결정론으로 계산(서버 권위).
        FitAnalysisAiResult skeleton = ruleEngine.generate(command);
        try {
            // 2) OpenAI 는 skeleton 값을 입력으로 받아 설명만 생성.
            StructuredResponse response = openAiClient.request(
                    FitExplainAssembler.EXPLAIN_SCHEMA_NAME,
                    assembler.explainSchema(),
                    FitAnalysisPromptCatalog.FIT_EXPLAIN_SYSTEM_PROMPT,
                    assembler.explainUserPrompt(command, skeleton));
            // 3) 검증(fitSummary/grounding) + 병합. 위반 시 예외 → 아래 FALLBACK.
            return assembler.assemble(command, skeleton, response.payload(), response.usage());
        } catch (RuntimeException exception) {
            // 최종 안전망: skeleton(=규칙엔진 결정론 결과)로 화면 보장. 판단값은 애초에 skeleton 소유라 동일하다.
            return new FitAnalysisAiResult(
                    skeleton.fitScore(),
                    skeleton.matchedSkills(),
                    skeleton.missingSkills(),
                    skeleton.recommendedStudy(),
                    skeleton.recommendedCertificates(),
                    skeleton.strategy(),
                    skeleton.scoreBasis(),
                    skeleton.gapRecommendations(),
                    skeleton.learningRoadmap(),
                    skeleton.certificateRecommendations(),
                    skeleton.strategyActions(),
                    skeleton.conditionMatrix(),
                    skeleton.applyDecision(),
                    new CareerAnalysisAiUsage("mock-fallback", 0, 0, 0, true),
                    "FALLBACK",
                    exception.getMessage(),
                    true);
        }
    }
}
