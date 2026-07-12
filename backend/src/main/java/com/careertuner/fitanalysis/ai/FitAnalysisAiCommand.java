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
 *
 * <p>{@code profileInsight} 는 A(profile_ai_analysis)에서 온 <b>지원자 프로필 AI 분석 요약</b>(강점·보완점 텍스트,
 * 없으면 {@code null})이다. 지원자 자신에 대한 분석이므로 설명·강점·위험요인 서술의 참고로만 쓰고, companyContext 와
 * 마찬가지로 판단값 계산에는 관여하지 않는다. A 분석이 언급한 기술을 '보유 확정'으로 단정하지 않는다(E1 가드 유지).
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
        String companyContext,
        boolean userRequested,
        String profileInsight
) {
    /** 사용자가 자격증 전략을 명시 요청했는지(학습/자격증 탭 등). true 여도 무조건 추천이 아니라 '평가'다 —
     * cert-need-gate 는 여전히 NOT_NEEDED/OPTIONAL_LOW_PRIORITY 를 낼 수 있다. */

    /** profileInsight 없는 하위호환 생성자(기업 맥락·요청 플래그까지). */
    public FitAnalysisAiCommand(String companyName, String jobTitle,
                                List<String> requiredSkills, List<String> preferredSkills, String duties,
                                List<String> profileSkills, List<String> profileCertificates, String desiredJob,
                                String companyContext, boolean userRequested) {
        this(companyName, jobTitle, requiredSkills, preferredSkills, duties,
                profileSkills, profileCertificates, desiredJob, companyContext, userRequested, null);
    }

    /** userRequested 없는 하위호환 생성자(기업 맥락까지). */
    public FitAnalysisAiCommand(String companyName, String jobTitle,
                                List<String> requiredSkills, List<String> preferredSkills, String duties,
                                List<String> profileSkills, List<String> profileCertificates, String desiredJob,
                                String companyContext) {
        this(companyName, jobTitle, requiredSkills, preferredSkills, duties,
                profileSkills, profileCertificates, desiredJob, companyContext, false, null);
    }

    /** 기업 맥락·요청 플래그 없는 하위호환 생성자(규칙엔진 테스트용). */
    public FitAnalysisAiCommand(String companyName, String jobTitle,
                                List<String> requiredSkills, List<String> preferredSkills, String duties,
                                List<String> profileSkills, List<String> profileCertificates, String desiredJob) {
        this(companyName, jobTitle, requiredSkills, preferredSkills, duties,
                profileSkills, profileCertificates, desiredJob, null, false, null);
    }
}
