package com.careertuner.fitanalysis.service;

import java.util.List;

import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.fitanalysis.dto.CareerCertificateStrategyResponse;
import com.careertuner.fitanalysis.dto.CareerRoadmapResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisHistoryEntryResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisLearningTaskResponse;

public interface FitAnalysisService {

    List<FitAnalysisDetailResponse> list(Long userId);

    FitAnalysisDetailResponse getByApplicationCase(Long userId, Long applicationCaseId);

    /**
     * 재분석 히스토리(최신순). 직전 분석 대비 점수 변화와 매칭/부족 역량 변화를 함께 계산한다.
     */
    List<FitAnalysisHistoryEntryResponse> getHistory(Long userId, Long applicationCaseId);

    /**
     * 지원 건의 공고 분석 결과와 사용자 프로필을 비교해 적합도 분석을 생성/저장한다(C 담당 AI 12~15).
     * API 키가 없으면 mock, 있으면 동일 흐름으로 실제 구조화 분석이 동작한다.
     */
    /** 기본 생성(자격증 전략 평가 미요청). */
    default FitAnalysisDetailResponse generate(Long userId, Long applicationCaseId) {
        return generate(userId, applicationCaseId, false);
    }

    /** certificateStrategy=true 면 학습/자격증 탭의 명시 요청으로 보고 자격증 관점을 함께 <b>평가</b>한다
     * (무조건 추천이 아니라 평가 — 결과는 NOT_NEEDED/OPTIONAL_LOW_PRIORITY 도 정상). 모델은 AUTO(현행 폴백). */
    default FitAnalysisDetailResponse generate(Long userId, Long applicationCaseId, boolean certificateStrategy) {
        return generate(userId, applicationCaseId, certificateStrategy, RequestedAiModel.AUTO);
    }

    /** 사용자가 AI 모델을 <b>명시 선택</b>하는 재분석 경로(기본 AUTO=현행). 모델 선택은 설명 생성 provider 만 바꾸고
     * 판단값(fitScore/matched/missing/applyDecision)은 규칙엔진이 소유해 어느 모델이든 동일하다. */
    FitAnalysisDetailResponse generate(Long userId, Long applicationCaseId, boolean certificateStrategy,
                                       RequestedAiModel requestedModel);

    /** 장기 커리어 자격증 전략(desiredJob 기준, 현재 지원 건 전략과 분리). 결정론 규칙만 사용(외부 API 미호출). */
    CareerCertificateStrategyResponse careerCertificateStrategy(Long userId);

    /** 장기 커리어 로드맵(결정론) — 확인된 실일정 + 월 단위 학습 계획 블록. */
    CareerRoadmapResponse careerRoadmap(Long userId, int months);

    FitAnalysisLearningTaskResponse updateLearningTask(Long userId,
                                                       Long fitAnalysisId,
                                                       Long taskId,
                                                       boolean completed);
}
