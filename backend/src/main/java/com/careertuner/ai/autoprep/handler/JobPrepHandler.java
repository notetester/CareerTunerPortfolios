package com.careertuner.ai.autoprep.handler;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.PrepProgress;
import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.ai.autoprep.PrepStepHandler;
import com.careertuner.ai.autoprep.PrepStepResult;
import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.service.JobAnalysisService;
import com.careertuner.jobposting.dto.JobPostingResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ② 공고 분석 (B). 지원 건의 채용공고를 AI로 분석해 핵심 요건·키워드를 뽑는다.
 * 지원 건이 없으면 건너뛴다. 이 결과는 이후 적합도(C) 단계의 입력이 된다.
 *
 * <p><b>게이트 확인자 원칙:</b> 산출물이 이미 준비됐거나(최신 공고 기준 분석 존재) 준비 중이면
 * (추출 자동 파이프라인 ANALYZING) 이 스텝은 실패가 아니라 충족이다 — 완료분은 즉시 사용, 진행 중이면
 * 완료를 기다렸다가 사용, 없을 때만 직접 계산한다. B 의 중복 실행 가드("이미 분석이 진행 중입니다",
 * JobAnalysisService.ensureAnalysisRunnable)는 그대로 두고 오케 측이 상태를 해석한다 — 추출 트랜잭션
 * 분리(SUCCEEDED 조기 가시화) 후 ANALYZING 창(~85초 실측)에서 run 이 시작되면 이 가드에 걸려
 * JOB 스텝이 FAILED 로 표기되던 충돌의 수리.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobPrepHandler implements PrepStepHandler {

    /**
     * 파이프라인 완료 대기 폴 간격·상한. 상한 근거: 추출 자동 파이프라인 전체 실측 85초
     * (2026-07-03 case 71 — job_analysis 는 시작 +25초 시점 출현) × 2 여유 ≈ 180초.
     * 상한 순서 관계(3자 정합): 이 대기(180s) < ④ EXTRACTING 리셋 게이트(300s, ChatbotController)
     * < FE 무이벤트 워치독(330s, useAutoPrepRun) — 대기가 항상 먼저 끝나므로 FE 침묵 타임아웃과
     * 겹치지 않고, 온보딩 게이트보다도 먼저 결론이 난다.
     */
    private static final long PIPELINE_WAIT_CAP_MS = 180_000L;
    private static final long PIPELINE_POLL_INTERVAL_MS = 3_000L;

    private final JobAnalysisService jobAnalysisService;
    private final ApplicationCaseService applicationCaseService;

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
        PrepStepResult fulfilled = awaitPipelineAnalysis(context, progress, start);
        if (fulfilled != null) {
            return fulfilled;
        }
        progress.substep("공고 파싱", "채용공고 본문 구조화");
        progress.substep("핵심 요건 추출", "필수·우대 요건 분석");
        context.checkActive();
        JobAnalysisResponse result =
                jobAnalysisService.createJobAnalysis(context.userId(), context.applicationCaseId());
        long ms = (System.nanoTime() - start) / 1_000_000;
        return PrepStepResult.done("JOB", "공고 분석 완료", result, ms);
    }

    /**
     * 산출물 선확인: 최신 공고(id+revision) 기준 분석이 이미 있으면 그 결과로 즉시 충족(재계산 없음 —
     * 유저 표기는 그냥 "완료"), 파이프라인이 만드는 중(ANALYZING)이면 완료를 폴링으로 기다렸다가 충족한다.
     * 산출물이 없고 파이프라인도 아니면(공고 교체·파이프라인 실패 복원 포함) null 을 돌려 기존 직접 계산
     * 경로로 떨어진다 — 무회귀. 확인 중 오류도 null 로 흡수해 원래 경로의 예외/메시지가 살아남게 한다.
     * 상한 초과만 진짜 실패(FAILED)다.
     */
    private PrepStepResult awaitPipelineAnalysis(PrepStepContext context, PrepProgress progress, long start) {
        Long userId = context.userId();
        Long caseId = context.applicationCaseId();
        long deadline = System.currentTimeMillis() + PIPELINE_WAIT_CAP_MS;
        boolean waiting = false;
        while (true) {
            context.checkActive();
            String status;
            try {
                JobAnalysisResponse latest = jobAnalysisService.getJobAnalysis(userId, caseId);
                if (isForLatestPosting(userId, caseId, latest)) {
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    return PrepStepResult.done("JOB", "공고 분석 완료", latest, ms);
                }
                status = applicationCaseService.get(userId, caseId).status();
            } catch (RuntimeException ex) {
                log.debug("공고 분석 선확인 실패 — 직접 계산 경로로 진행: {}", ex.getMessage());
                return null;
            }
            if (!"ANALYZING".equals(status)) {
                return null;
            }
            if (System.currentTimeMillis() >= deadline) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                return PrepStepResult.failed("JOB",
                        "공고 분석 완료를 기다렸지만 시간이 초과됐어요. 잠시 후 다시 시도해 주세요.", ms);
            }
            if (!waiting) {
                waiting = true;
                progress.substep("공고 분석 확인", "자동 분석이 진행 중 — 완료를 기다려요");
            }
            try {
                Thread.sleep(PIPELINE_POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    /** 최신 공고(id+revision)에 대한 분석인지 — 공고가 교체/수정됐으면 재계산 경로를 타야 한다. */
    private boolean isForLatestPosting(Long userId, Long caseId, JobAnalysisResponse latest) {
        if (latest == null) {
            return false;
        }
        JobPostingResponse posting = applicationCaseService.getJobPosting(userId, caseId);
        return posting != null
                && Objects.equals(posting.id(), latest.jobPostingId())
                && Objects.equals(posting.revision(), latest.jobPostingRevision());
    }
}
