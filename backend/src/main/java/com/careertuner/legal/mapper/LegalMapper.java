package com.careertuner.legal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.legal.domain.LegalClause;
import com.careertuner.legal.domain.LegalDocumentVersion;

/**
 * 공개 법적 문서 조회 매퍼. 공개 조회는 (버전 1건) + (조항 일괄) 2쿼리로 N+1 을 피한다.
 */
@Mapper
public interface LegalMapper {

    /**
     * 현재 시행본(live) 1건. PUBLISHED 중 effective_date<=NOW() 의 최신본을
     * effective_date DESC, published_at DESC, id DESC tiebreaker 로 결정한다.
     * 없으면 null.
     */
    LegalDocumentVersion findLiveVersion(@Param("docType") String docType);

    /** 버전의 조항 일괄 조회 (seq 오름차순). */
    List<LegalClause> findClausesByVersionId(@Param("versionId") Long versionId);
}
