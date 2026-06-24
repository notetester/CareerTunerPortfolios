package com.careertuner.jobanalysis.ai;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;

public interface JobAnalysisAiService {

    JobAnalysisPayload analyze(ApplicationCase applicationCase, String sourceText);
}
