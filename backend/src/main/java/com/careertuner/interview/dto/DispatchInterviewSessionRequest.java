package com.careertuner.interview.dto;

/** 면접 세션을 이어받을 대상. 무본문 또는 null target은 기존 MOBILE 동작과 호환한다. */
public record DispatchInterviewSessionRequest(InterviewDispatchTarget target) {

    public InterviewDispatchTarget targetOrDefault() {
        return target != null ? target : InterviewDispatchTarget.MOBILE;
    }
}
