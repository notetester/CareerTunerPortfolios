package com.careertuner.companyanalysis.websearch;

/**
 * 웹검색 실패(키 미설정·타임아웃·API 오류 등). 하이브리드 폴백 계층(235 §5)에서
 * 검색 실패 → 호스티드 폴백 판단의 트리거가 되므로 BusinessException 대신 도메인 예외로 둔다.
 * 메시지에 시크릿(Client Secret)을 절대 포함하지 않는다.
 */
public class CompanyWebSearchException extends RuntimeException {

    public CompanyWebSearchException(String message) {
        super(message);
    }

    public CompanyWebSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
