package com.careertuner.companyjobposting.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.companyjobposting.domain.CompanyJobPosting;
import com.careertuner.companyjobposting.domain.CompanyJobPostingRevision;
import com.careertuner.companyjobposting.dto.JobPostingReviewRow;
import com.careertuner.companyjobposting.dto.JobPostingSearchCriteria;

@Mapper
public interface CompanyJobPostingMapper {

    // ── 공고 본체 ──

    void insert(CompanyJobPosting posting);

    CompanyJobPosting findById(Long id);

    /** 검토/수정 경합 방지용 비관적 잠금 조회. */
    CompanyJobPosting findByIdForUpdate(Long id);

    /** 내 공고 목록(기업 관리 화면) — 수정 검토 대기 여부 파생 컬럼 포함. */
    List<CompanyJobPosting> findMine(Long companyUserId);

    /** 상세 필드 전체 갱신(직접 수정 경로와 revision 승인 반영 공용). */
    void updateFields(CompanyJobPosting posting);

    void updateStatus(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("rejectReason") String rejectReason,
                      @Param("reviewedBy") Long reviewedBy);

    /** 게시 확정 — published_at 은 최초 1회만 채운다. */
    void markPublished(@Param("id") Long id, @Param("reviewedBy") Long reviewedBy);

    void markClosed(Long id);

    void increaseViewCount(Long id);

    // ── 공개 게시판 ──

    List<CompanyJobPosting> searchPublished(JobPostingSearchCriteria criteria);

    long countPublished(JobPostingSearchCriteria criteria);

    // ── 검토 큐 / revision ──

    List<JobPostingReviewRow> findReviewQueue();

    void insertRevision(CompanyJobPostingRevision revision);

    CompanyJobPostingRevision findPendingRevisionByPostingId(Long postingId);

    /** 대기 중 변경본 payload 교체(재제출 시 새 행 대신 갱신). */
    void updateRevisionPayload(@Param("id") Long id, @Param("payloadJson") String payloadJson);

    void reviewRevision(@Param("id") Long id,
                        @Param("status") String status,
                        @Param("rejectReason") String rejectReason,
                        @Param("reviewedBy") Long reviewedBy);

    // ── 정책 조회(읽기 전용) ──

    /** JOB_POSTING_REVIEW_POLICY config_json. 비활성/미존재면 null → 코드 기본값 사용. */
    String selectReviewPolicyJson();
}
