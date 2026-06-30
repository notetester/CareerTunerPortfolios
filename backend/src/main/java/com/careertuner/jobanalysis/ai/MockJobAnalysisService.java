package com.careertuner.jobanalysis.ai;

import org.springframework.stereotype.Component;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;

/**
 * 공고 분석 최종 폴백(목업) — 외부 provider(OSS·Claude·OpenAI)가 모두 미설정/실패해도 화면이 깨지지 않게 한다.
 *
 * <p>외부 호출 없이 형식상 유효하고 비어 있지 않은 {@link JobAnalysisPayload} 를 즉시 반환한다(항상 성공).
 * 실제 분석이 아니라 시연용 임시 결과이므로 사용량은 0토큰·model "mock" 으로 기록해 과금/통계와 구분한다.
 */
@Component
public class MockJobAnalysisService implements JobAnalysisAiService {

    @Override
    public JobAnalysisPayload analyze(ApplicationCase applicationCase, String sourceText) {
        return new JobAnalysisPayload(
                "정규직",
                "경력 무관",
                "[\"기본 직무 역량\",\"커뮤니케이션\"]",
                "[\"관련 자격증\"]",
                "데모 목업: 채용공고의 주요 담당 업무 요약입니다.",
                "데모 목업: 자격 요건 요약입니다.",
                "보통",
                "데모 목업 공고 분석입니다. 외부 AI 미연결 상태의 임시 결과로, 실제 분석이 아닙니다.",
                "[]",
                "[]",
                new Usage("mock", 0, 0, 0));
    }
}
