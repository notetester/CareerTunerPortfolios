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
    private final OpenAiJobAnalysisService openAiService;
    private final OssJobAnalysisClient ossClient;
    private final JobAnalysisQualityGate qualityGate;
    private final boolean ossAvailable;

    public JobAnalysisAiProvider(JobAnalysisAiProperties properties,
                                  OpenAiJobAnalysisService openAiService,
                                  ObjectProvider<OssJobAnalysisClient> ossProvider,
                                  JobAnalysisQualityGate qualityGate) {
        this.properties = properties;
        this.openAiService = openAiService;
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

    public JobAnalysisPayload analyze(ApplicationCase applicationCase, String sourceText) {
        if (!ossAvailable) {
            return openAiService.analyze(applicationCase, sourceText);
        }

        try {
            JobAnalysisPayload ossResult = ossClient.analyze(applicationCase, sourceText);

            if (qualityGate.isAcceptable(ossResult)) {
                log.info("Job analysis: OSS result accepted (case={})", applicationCase.getId());
                return ossResult;
            }

            log.info("Job analysis: OSS result rejected by quality gate, falling back to OpenAI (case={})",
                    applicationCase.getId());
        } catch (RuntimeException ex) {
            log.warn("Job analysis: OSS failed, falling back to OpenAI (case={}): {}",
                    applicationCase.getId(), ex.getMessage());
        }

        return openAiService.analyze(applicationCase, sourceText);
    }

    public String providerName() {
        return ossAvailable ? "oss" : "openai";
    }
}
