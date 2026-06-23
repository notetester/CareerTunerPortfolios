package com.careertuner.ai.autoprep.handler;

import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.ai.autoprep.PrepStepHandler;
import com.careertuner.ai.autoprep.PrepStepResult;
import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.fitanalysis.service.FitAnalysisService;

import lombok.RequiredArgsConstructor;

/**
 * ③ 적합도 분석 (C). 프로필·공고 분석을 바탕으로 지원 건 적합도를 산출한다(자체 LLM + 근거 가드).
 * 지원 건이 없으면 건너뛴다. 내부적으로 공고 분석(B) 결과를 소스로 읽으므로 JOB 단계 뒤에 실행된다.
 */
@Component
@RequiredArgsConstructor
public class FitPrepHandler implements PrepStepHandler {

    private final FitAnalysisService fitAnalysisService;

    @Override
    public String key() {
        return "FIT";
    }

    @Override
    public PrepStepResult handle(PrepStepContext context) {
        if (context.applicationCaseId() == null) {
            return PrepStepResult.skipped("FIT", "지원 건이 없어 건너뜀");
        }
        long start = System.nanoTime();
        FitAnalysisDetailResponse result =
                fitAnalysisService.generate(context.userId(), context.applicationCaseId());
        long ms = (System.nanoTime() - start) / 1_000_000;
        return PrepStepResult.done("FIT", "적합도 분석 완료", result, ms);
    }
}
