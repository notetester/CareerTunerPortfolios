package com.careertuner.admin.legal.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.legal.domain.LegalClause;
import com.careertuner.legal.domain.LegalDocumentVersion;

/**
 * 관리자 법적 문서 매퍼. 버전 CRUD/게시 + 조항 일괄 교체.
 */
@Mapper
public interface AdminLegalMapper {

    /** doc_type 의 버전 목록 (조항 수 포함, effective_date/published_at/id 내림차순). */
    List<AdminVersionRow> findVersionsByDocType(@Param("docType") String docType);

    /** 버전 1건 (없으면 null). */
    LegalDocumentVersion findVersionById(@Param("id") Long id);

    /** 버전의 조항 일괄 (seq 오름차순). */
    List<LegalClause> findClausesByVersionId(@Param("versionId") Long versionId);

    /** 현행 시행본(live) 1건 — 조항 복제용. 없으면 null. */
    LegalDocumentVersion findLiveVersion(@Param("docType") String docType);

    /** doc_type 의 DRAFT 개수 (DRAFT 1건 제약 검증용). */
    int countDrafts(@Param("docType") String docType);

    /** 버전의 조항 개수 (게시 전 0개 차단용). */
    int countClauses(@Param("versionId") Long versionId);

    /** 새 초안 INSERT. 생성된 id 는 version.id 에 채워진다(useGeneratedKeys). */
    void insertVersion(LegalDocumentVersion version);

    /** 초안 메타 UPDATE (DRAFT 만 호출). */
    void updateDraftMeta(@Param("id") Long id,
                         @Param("versionLabel") String versionLabel,
                         @Param("summary") String summary,
                         @Param("isAdverse") boolean isAdverse,
                         @Param("effectiveDate") LocalDateTime effectiveDate);

    /** 게시: status=PUBLISHED, published_at, effective_date 설정. */
    void publishVersion(@Param("id") Long id,
                        @Param("effectiveDate") LocalDateTime effectiveDate);

    /** 버전 소프트 삭제 (DRAFT 만 호출). */
    void deleteVersion(@Param("id") Long id);

    /** 버전의 기존 조항 전체 소프트 삭제 (저장 시 새 스냅샷 삽입 전 호출). */
    void deleteClausesByVersionId(@Param("versionId") Long versionId);

    /** 조항 일괄 INSERT. */
    void insertClauses(@Param("versionId") Long versionId,
                       @Param("clauses") List<LegalClause> clauses);
}
