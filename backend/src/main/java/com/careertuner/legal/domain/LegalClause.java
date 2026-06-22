package com.careertuner.legal.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 법적 문서 조항 (legal_clause). 버전에 종속(1:N, ON DELETE CASCADE).
 * body 의 줄바꿈이 항(1.2.3.) 구분이다. embedding 은 AI B(RAG) 예약 컬럼.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalClause {

    private Long id;
    private Long versionId;
    private int seq;        // 조항 순서 (제N조)
    private String title;
    private String body;
}
