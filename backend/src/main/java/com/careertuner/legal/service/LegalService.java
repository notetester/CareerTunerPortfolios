package com.careertuner.legal.service;

import com.careertuner.legal.dto.LegalDocResponse;

public interface LegalService {

    /**
     * 공개 법적 문서 조회. docType = terms|privacy|marketing.
     * 시행본이 없으면 빈 sections 와 라벨 title 만 채워 반환한다(404 아님).
     */
    LegalDocResponse getPublicDoc(String docType);
}
