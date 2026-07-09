package com.careertuner.fitanalysis.ai;

import java.util.List;

/**
 * 적합도 분석 AI 입력 묶음.
 *
 * <p>A의 프로필 스냅샷과 B의 공고 분석 결과를 읽기 전용 기준 데이터로 받아 적합도 분석에 사용한다.
 * 원본 데이터(프로필/공고 분석)는 각 담당 영역에서 관리하며 C는 수정하지 않는다.
 *
 * <p>{@code companyContext} 는 B(company_analysis)에서 온 <b>기업 맥락</b>(회사 요약·최근 이슈·면접 포인트)의
 * 사전 조립 텍스트다(없으면 {@code null}). <b>설명 생성(strategy/strategyActions)에만</b> 쓰이고, 규칙엔진의
 * 판단값(fitScore/matched/missing/applyDecision) 계산에는 관여하지 않는다 — 뉴로-심볼릭 불변식 유지.
 */
public record FitAnalysisAiCommand(
        String companyName,
        String jobTitle,
        List<String> requiredSkills,
        List<String> preferredSkills,
        String duties,
        List<String> profileSkills,
        List<String> profileCertificates,
        String desiredJob,
        String companyContext
) {
    /** 기업 맥락 없는 하위호환 생성자(기존 호출부·규칙엔진 테스트용). */
    public FitAnalysisAiCommand(String companyName, String jobTitle,
                                List<String> requiredSkills, List<String> preferredSkills, String duties,
                                List<String> profileSkills, List<String> profileCertificates, String desiredJob) {
        this(companyName, jobTitle, requiredSkills, preferredSkills, duties,
                profileSkills, profileCertificates, desiredJob, null);
    }
}
