package com.careertuner.user.dto;

/**
 * 이력서 상세 스펙 저장 요청.
 *
 * <p>각 필드는 프런트에서 리스트/객체로 오며 서버가 JSON 문자열로 직렬화해 저장한다.
 * 유연한 확장을 위해 Object 타입으로 받는다(user_profile 저장 패턴과 동일).</p>
 */
public record UserResumeDetailRequest(
        Object education,
        Object career,
        Object certificates,
        Object languages,
        Object awards,
        Object activities,
        Object skills,
        Object portfolios,
        Object desiredCondition) {
}
