package com.careertuner.correction.ai;

import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;

public interface CorrectionAiProvider {

    CorrectionPayload correct(CorrectionCommand command);
}
