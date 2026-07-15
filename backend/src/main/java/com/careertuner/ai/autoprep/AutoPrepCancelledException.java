package com.careertuner.ai.autoprep;

/** 사용자 연결 종료로 AutoPrep 협력적 취소가 요청됐음을 나타내는 내부 예외. */
public final class AutoPrepCancelledException extends RuntimeException {

    public AutoPrepCancelledException() {
        super("AutoPrep 실행이 취소되었습니다.");
    }
}
