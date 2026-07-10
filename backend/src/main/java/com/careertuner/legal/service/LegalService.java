package com.careertuner.legal.service;

import com.careertuner.legal.dto.LegalDocResponse;

public interface LegalService {

    /**
     * 공개 법적 문서 조회. 지원하는 슬러그는 LegalDocType에서 관리한다.
     * 관리자 시행본이 없으면 코드에 포함된 기본 문서를 반환한다.
     */
    LegalDocResponse getPublicDoc(String docType);
}
