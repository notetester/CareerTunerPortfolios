package com.careertuner.jobanalysis.ai;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JobAnalysisAiProvider {

    private final JobAnalysisAiProperties properties;
    private final AnthropicJobAnalysisService anthropicService;
    private final OpenAiJobAnalysisService openAiService;
    private final MockJobAnalysisService mockService;
    private final OssJobAnalysisClient ossClient;
    private final JobAnalysisQualityGate qualityGate;
    private final boolean ossAvailable;

    public JobAnalysisAiProvider(JobAnalysisAiProperties properties,
                                  AnthropicJobAnalysisService anthropicService,
                                  OpenAiJobAnalysisService openAiService,
                                  MockJobAnalysisService mockService,
                                  ObjectProvider<OssJobAnalysisClient> ossProvider,
                                  JobAnalysisQualityGate qualityGate) {
        this.properties = properties;
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
        this.mockService = mockService;
        this.ossClient = ossProvider.getIfAvailable();
        this.qualityGate = qualityGate;
        this.ossAvailable = properties.isOss() && this.ossClient != null && properties.configured();

        if (this.ossAvailable) {
            log.info("Job analysis AI provider: OSS (model={}, url={})",
                    properties.getModel(), properties.getBaseUrl());
        } else {
            log.info("Job analysis AI provider: OpenAI (oss={}, configured={})",
                    properties.isOss(), properties.configured());
        }
    }

    /**
     * 공고 분석 폴백 체인: OSS(품질 게이트) → Claude(Haiku, 1차 폴백) → OpenAI(2차 폴백) → 목업(최종).
     * 어느 단계가 죽거나 응답이 부적합해도 다음으로 넘어가며, 마지막 목업은 항상 성공해 절대 예외로 끝나지 않는다.
     */
    public JobAnalysisPayload analyze(ApplicationCase applicationCase, String sourceText) {
        // 1) 자체 모델(OSS) — 가용 + 품질 게이트 통과 시 채택.
        if (ossAvailable) {
            try {
                JobAnalysisPayload ossResult = ossClient.analyze(applicationCase, sourceText);
                if (qualityGate.isAcceptable(ossResult)) {
                    log.info("Job analysis: OSS result accepted (case={})", applicationCase.getId());
                    return ossResult;
                }
                log.info("Job analysis: OSS result rejected by quality gate → Claude 폴백 (case={})",
                        applicationCase.getId());
            } catch (RuntimeException ex) {
                log.warn("Job analysis: OSS failed → Claude 폴백 (case={}): {}",
                        applicationCase.getId(), ex.getMessage());
            }
        }
        // 2) 1차 폴백: Claude(Haiku) — 공통 키라 가장 안정적. 키 없으면 건너뛰고, 실패하면 OpenAI 로.
        if (anthropicService.configured()) {
            try {
                return anthropicService.analyze(applicationCase, sourceText);
            } catch (RuntimeException ex) {
                log.warn("Job analysis: Claude failed → OpenAI 폴백 (case={}): {}",
                        applicationCase.getId(), ex.getMessage());
            }
        }
        // 3) 2차 폴백: OpenAI. 실패하면 목업으로.
        try {
            return openAiService.analyze(applicationCase, sourceText);
        } catch (RuntimeException ex) {
            log.warn("Job analysis: OpenAI failed → 목업 폴백 (case={}): {}",
                    applicationCase.getId(), ex.getMessage());
        }
        // 4) 최종 폴백: 목업 — 외부 provider 가 모두 미설정/실패해도 화면이 깨지지 않게 한다.
        return mockService.analyze(applicationCase, sourceText);
    }

    public String providerName() {
        return ossAvailable ? "oss" : "openai";
    }
}
