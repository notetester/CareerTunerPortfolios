package com.careertuner.jobanalysis.ai;

import org.springframework.stereotype.Component;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.OpenAiResponsesClient;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OpenAiJobAnalysisService implements JobAnalysisAiService {

    private final OpenAiResponsesClient openAiClient;

    @Override
    public JobAnalysisPayload analyze(ApplicationCase applicationCase, String sourceText) {
        return openAiClient.analyzeJobPosting(applicationCase, sourceText);
    }
}
