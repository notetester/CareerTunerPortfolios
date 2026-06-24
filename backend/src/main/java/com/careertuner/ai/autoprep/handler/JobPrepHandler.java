package com.careertuner.ai.autoprep.handler;

import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.PrepProgress;
import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.ai.autoprep.PrepStepHandler;
import com.careertuner.ai.autoprep.PrepStepResult;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.service.JobAnalysisService;

import lombok.RequiredArgsConstructor;

/**
 * ② 공고 분석 (B). 지원 건의 채용공고를 AI로 분석해 핵심 요건·키워드를 뽑는다.
 * 지원 건이 없으면 건너뛴다. 이 결과는 이후 적합도(C) 단계의 입력이 된다.
 */
@Component
@RequiredArgsConstructor
public class JobPrepHandler implements PrepStepHandler {

    private final JobAnalysisService jobAnalysisService;

    @Override
    public String key() {
        return "JOB";
    }

    @Override
    public PrepStepResult handle(PrepStepContext context, PrepProgress progress) {
        if (context.applicationCaseId() == null) {
            return PrepStepResult.skipped("JOB", "지원 건이 없어 건너뜀 — 공고를 먼저 등록하세요.");
        }
        long start = System.nanoTime();
        progress.substep("공고 파싱", "채용공고 본문 구조화");
        progress.substep("핵심 요건 추출", "필수·우대 요건 분석");
        JobAnalysisResponse result =
                jobAnalysisService.createJobAnalysis(context.userId(), context.applicationCaseId());
        long ms = (System.nanoTime() - start) / 1_000_000;
        return PrepStepResult.done("JOB", "공고 분석 완료", result, ms);
    }
}
