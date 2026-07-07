package com.careertuner.companyjobposting.service;

import java.util.List;

import com.careertuner.companyjobposting.dto.CompanyJobPostingResponse;
import com.careertuner.companyjobposting.dto.JobBoardAnalyzeResponse;
import com.careertuner.companyjobposting.dto.JobPostingPageResponse;
import com.careertuner.companyjobposting.dto.JobPostingReviewDetailResponse;
import com.careertuner.companyjobposting.dto.JobPostingReviewRow;
import com.careertuner.companyjobposting.dto.JobPostingUpsertRequest;

public interface CompanyJobPostingService {

    // ── 기업(COMPANY) 측 ──

    /** 공고 생성. submit=true 면 신뢰등급 정책에 따라 PENDING_REVIEW 또는 즉시 PUBLISHED. */
    CompanyJobPostingResponse create(Long companyUserId, JobPostingUpsertRequest request);

    /**
     * 공고 수정. DRAFT/REJECTED/PENDING_REVIEW 는 직접 갱신,
     * PUBLISHED 는 정책에 따라 즉시 반영 또는 수정 검토(revision) 제출.
     */
    CompanyJobPostingResponse update(Long companyUserId, Long postingId, JobPostingUpsertRequest request);

    List<CompanyJobPostingResponse> listMine(Long companyUserId);

    CompanyJobPostingResponse getMine(Long companyUserId, Long postingId);

    CompanyJobPostingResponse close(Long companyUserId, Long postingId);

    // ── 공개 게시판 ──

    JobPostingPageResponse search(String keyword, String jobRole, String location,
                                  String employmentType, String careerLevel,
                                  String sort, int page, int size);

    /** PUBLISHED 공고만. 조회수 증가 포함. */
    CompanyJobPostingResponse getPublished(Long postingId);

    /** "이 공고로 분석하기" — 공고 본문 텍스트로 지원 건을 생성한다(기존 생성 서비스 재사용). */
    JobBoardAnalyzeResponse analyze(Long userId, Long postingId);

    // ── 관리자 검토 ──

    List<JobPostingReviewRow> reviewQueue();

    JobPostingReviewDetailResponse reviewDetail(Long postingId);

    /** 승인 — 수정 검토가 대기 중이면 변경본 반영, 아니면 신규 공고 게시. */
    void approveReview(Long adminId, Long postingId);

    /** 반려 — 사유 필수. */
    void rejectReview(Long adminId, Long postingId, String reason);
}
