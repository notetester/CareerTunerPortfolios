package com.careertuner.interview.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 음성/영상 면접의 온디바이스 분석 결과 (interview_media_analysis).
 * 원본 음성·영상은 서버에 저장하지 않고, 트랜스크립트와 점수(JSON)만 보관한다 (ADR-002).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewMediaAnalysis {

    private Long id;
    private Long interviewSessionId;
    /** 답변 단위 분석일 때 연결되는 질문. 기존 세션 단위 분석은 null이다. */
    private Long questionId;
    /** 답변 단위 분석일 때 연결되는 답변. 기존 세션 단위 분석은 null이다. */
    private Long answerId;
    /** VOICE(음성 모의면접) / AVATAR(아바타 화상 면접) */
    private String kind;
    /** 대화 트랜스크립트 JSON 문자열: [{"role":"ai|user","text":"..."}] */
    private String transcript;
    /** 측정 지표 원본 JSON 문자열 (말속도·침묵·필러·피치, 표정/자세, 음성 프로필 등) */
    private String metrics;
    /** 종합 점수 0~100 */
    private Integer score;
    /** 항목별 점수 JSON 문자열: {"pace":80,"fluency":70,...} */
    private String scoreDetail;
    private LocalDateTime createdAt;
}
