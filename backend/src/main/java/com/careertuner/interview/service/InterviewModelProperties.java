package com.careertuner.interview.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 면접 도메인 OpenAI 모델 선택(2티어).
 *
 * <p>단일 {@code gpt-5} 사용은 추론량 과다로 느리고 비싸므로 작업 성격에 맞춰 모델을 분리한다.
 * 결정 배경·근거는 todo.md 결정 기록(추후 {@code docs/adr/} ADR로 정식화) 참고.
 * <ul>
 *   <li>생성(질문·모범답안·꼬리질문·리포트): 빠르고 저렴, 품질 충분한 모델</li>
 *   <li>채점(답변 평가·Critic): 채점 공정성을 위해 한 단계 위 모델</li>
 * </ul>
 * 기본값은 코드에 내장하고 환경변수({@code CAREERTUNER_INTERVIEW_MODEL_*})로 덮어쓸 수 있다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.interview.model")
public class InterviewModelProperties {

    /** 생성 작업(질문·모범답안·꼬리질문·리포트)용. */
    private String generation = "gpt-5.4-mini";
    /** 채점 작업(답변 평가·Critic 검증)용. */
    private String judge = "gpt-5.4";
}
