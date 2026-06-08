package com.careertuner.admin.prompt.fitanalysis.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.careertuner.admin.prompt.fitanalysis.dto.AdminFitAnalysisPromptResponse;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

@Service
public class AdminFitAnalysisPromptServiceImpl implements AdminFitAnalysisPromptService {

    private static final List<AdminFitAnalysisPromptResponse> TEMPLATES = List.of(
            new AdminFitAnalysisPromptResponse(
                    "FIT_SCORE_COMPARISON",
                    "공고-스펙 적합도 점수 산정",
                    "v0.1",
                    "DRAFT",
                    "지원자의 프로필과 공고 요구사항을 비교해 0~100점 적합도와 근거를 생성한다.",
                    List.of("공고 필수 역량", "공고 우대 역량", "사용자 기술 스택", "프로젝트/경력", "자격증"),
                    List.of("fitScore", "matchedSkills", "missingSkills", "scoreReasons"),
                    List.of("점수 산정 근거가 매칭/부족 역량과 일치하는지 확인", "70점 이상 추천 시 부족 역량 보완 계획이 포함되는지 확인", "근거 없는 단정 표현 제거"),
                    List.of("사용자 경력을 과장하지 않도록 제한", "학력/나이/성별 같은 비직무 요소를 점수에 반영하지 않음"),
                    LocalDate.of(2026, 6, 8)),
            new AdminFitAnalysisPromptResponse(
                    "LEARNING_RECOMMENDATION",
                    "부족 역량 학습·자격증 추천",
                    "v0.1",
                    "DRAFT",
                    "반복 부족 역량을 기준으로 학습 우선순위와 자격증 후보를 추천한다.",
                    List.of("missingSkills", "jobTitle", "experienceLevel", "userCertificates"),
                    List.of("recommendedStudy", "recommendedCertificates", "priority"),
                    List.of("추천 항목이 부족 역량과 직접 연결되는지 확인", "초급/중급 난이도가 사용자 경력과 맞는지 확인", "자격증 추천이 직무와 무관하게 남발되지 않는지 확인"),
                    List.of("특정 유료 강의나 기관을 필수처럼 표현하지 않음", "취득 기간을 확정적으로 보장하지 않음"),
                    LocalDate.of(2026, 6, 8)),
            new AdminFitAnalysisPromptResponse(
                    "APPLICATION_STRATEGY",
                    "지원 전략 문장 생성",
                    "v0.1",
                    "DRAFT",
                    "강점, 부족 역량, 공고 난이도를 조합해 다음 지원 액션을 요약한다.",
                    List.of("fitScore", "matchedSkills", "missingSkills", "companyName", "jobTitle"),
                    List.of("strategy", "nextActions", "interviewFocus"),
                    List.of("낮은 점수에서도 지원 가능성을 완전히 차단하지 않는지 확인", "실행 가능한 다음 행동이 1개 이상 포함되는지 확인", "면접 준비 포인트가 공고와 연결되는지 확인"),
                    List.of("불합격 가능성을 확정적으로 표현하지 않음", "개인 신상 기반 조언을 생성하지 않음"),
                    LocalDate.of(2026, 6, 8)));

    @Override
    public List<AdminFitAnalysisPromptResponse> list() {
        return TEMPLATES;
    }

    @Override
    public AdminFitAnalysisPromptResponse get(String key) {
        return TEMPLATES.stream()
                .filter(template -> template.key().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "적합도 분석 프롬프트를 찾을 수 없습니다."));
    }
}
