package com.careertuner.companyjobposting.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.dto.ApplicationCaseFromJobPostingResponse;
import com.careertuner.applicationcase.dto.CreateApplicationCaseFromJobPostingRequest;
import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.company.domain.CompanyProfile;
import com.careertuner.company.mapper.CompanyProfileMapper;
import com.careertuner.companyjobposting.domain.CompanyJobPosting;
import com.careertuner.companyjobposting.domain.CompanyJobPostingRevision;
import com.careertuner.companyjobposting.dto.CompanyJobPostingResponse;
import com.careertuner.companyjobposting.dto.JobBoardAnalyzeResponse;
import com.careertuner.companyjobposting.dto.JobPostingPageResponse;
import com.careertuner.companyjobposting.dto.JobPostingReviewDetailResponse;
import com.careertuner.companyjobposting.dto.JobPostingReviewRow;
import com.careertuner.companyjobposting.dto.JobPostingSearchCriteria;
import com.careertuner.companyjobposting.dto.JobPostingUpsertRequest;
import com.careertuner.companyjobposting.event.JobPostingPublishedEvent;
import com.careertuner.companyjobposting.event.JobPostingReviewedEvent;
import com.careertuner.companyjobposting.event.JobPostingSubmittedEvent;
import com.careertuner.companyjobposting.mapper.CompanyJobPostingMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 기업 채용공고 게시판 서비스.
 *
 * <p>상태 규칙:
 * <ul>
 *   <li>DRAFT/REJECTED 에서 제출 → 정책(createRequiresReview)에 따라 PENDING_REVIEW 또는 PUBLISHED</li>
 *   <li>PENDING_REVIEW 수정 → 제자리 갱신(검토 대기 유지)</li>
 *   <li>PUBLISHED 수정 → 정책(updateRequiresReview)에 따라 즉시 반영 또는 revision 검토 제출</li>
 *   <li>PUBLISHED 확정 시점마다 JobPostingPublishedEvent → RECOMMENDED_JOB 팬아웃(AFTER_COMMIT)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyJobPostingServiceImpl implements CompanyJobPostingService {

    private static final Set<String> EMPLOYMENT_TYPES =
            Set.of("FULL_TIME", "CONTRACT", "INTERN", "PART_TIME", "FREELANCE");
    private static final Set<String> CAREER_LEVELS = Set.of("NEW", "EXPERIENCED", "ANY");
    private static final Set<String> EDUCATION_LEVELS =
            Set.of("ANY", "HIGH_SCHOOL", "COLLEGE", "BACHELOR", "MASTER", "DOCTOR");
    private static final Set<String> SORTS = Set.of("latest", "deadline", "views");
    private static final int MAX_PAGE_SIZE = 50;

    private final CompanyJobPostingMapper postingMapper;
    private final CompanyProfileMapper profileMapper;
    private final JobPostingReviewPolicy reviewPolicy;
    private final ApplicationCaseService applicationCaseService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // ── 기업 측 ──

    @Override
    @Transactional
    public CompanyJobPostingResponse create(Long companyUserId, JobPostingUpsertRequest request) {
        CompanyProfile profile = requireCompany(companyUserId);
        boolean submit = Boolean.TRUE.equals(request.submit());

        CompanyJobPosting posting = toPosting(request);
        posting.setCompanyUserId(companyUserId);
        if (!submit) {
            posting.setStatus("DRAFT");
        } else if (reviewPolicy.requiresReview(profile.getTrustGrade(), false)) {
            posting.setStatus("PENDING_REVIEW");
        } else {
            posting.setStatus("PUBLISHED");
            posting.setPublishedAt(LocalDateTime.now());
        }
        postingMapper.insert(posting);

        if ("PENDING_REVIEW".equals(posting.getStatus())) {
            eventPublisher.publishEvent(new JobPostingSubmittedEvent(posting.getId(), null, posting.getTitle()));
        } else if ("PUBLISHED".equals(posting.getStatus())) {
            eventPublisher.publishEvent(new JobPostingPublishedEvent(posting.getId()));
        }
        return toResponse(postingMapper.findById(posting.getId()));
    }

    @Override
    @Transactional
    public CompanyJobPostingResponse update(Long companyUserId, Long postingId, JobPostingUpsertRequest request) {
        CompanyProfile profile = requireCompany(companyUserId);
        CompanyJobPosting existing = requireOwnedForUpdate(companyUserId, postingId);
        boolean submit = Boolean.TRUE.equals(request.submit());

        switch (existing.getStatus()) {
            case "DRAFT", "REJECTED" -> {
                CompanyJobPosting updated = toPosting(request);
                updated.setId(postingId);
                if (!submit) {
                    updated.setStatus(existing.getStatus());
                } else if (reviewPolicy.requiresReview(profile.getTrustGrade(), false)) {
                    updated.setStatus("PENDING_REVIEW");
                } else {
                    updated.setStatus("PUBLISHED");
                }
                postingMapper.updateFields(updated);
                postingMapper.updateStatus(postingId, updated.getStatus(), null, null);
                if ("PENDING_REVIEW".equals(updated.getStatus())) {
                    eventPublisher.publishEvent(new JobPostingSubmittedEvent(postingId, null, updated.getTitle()));
                } else if ("PUBLISHED".equals(updated.getStatus())) {
                    postingMapper.markPublished(postingId, null);
                    eventPublisher.publishEvent(new JobPostingPublishedEvent(postingId));
                }
            }
            case "PENDING_REVIEW" -> {
                // 검토 대기 중 수정 — 제자리 갱신(검토 대기 유지, 재팬아웃 없음)
                CompanyJobPosting updated = toPosting(request);
                updated.setId(postingId);
                updated.setStatus("PENDING_REVIEW");
                postingMapper.updateFields(updated);
            }
            case "PUBLISHED" -> {
                if (reviewPolicy.requiresReview(profile.getTrustGrade(), true)) {
                    String payload = writePayload(request);
                    CompanyJobPostingRevision pending = postingMapper.findPendingRevisionByPostingId(postingId);
                    Long revisionId;
                    if (pending != null) {
                        postingMapper.updateRevisionPayload(pending.getId(), payload);
                        revisionId = pending.getId();
                    } else {
                        CompanyJobPostingRevision revision = CompanyJobPostingRevision.builder()
                                .jobPostingId(postingId)
                                .payloadJson(payload)
                                .status("PENDING")
                                .build();
                        postingMapper.insertRevision(revision);
                        revisionId = revision.getId();
                    }
                    eventPublisher.publishEvent(new JobPostingSubmittedEvent(postingId, revisionId, existing.getTitle()));
                } else {
                    CompanyJobPosting updated = toPosting(request);
                    updated.setId(postingId);
                    updated.setStatus("PUBLISHED");
                    postingMapper.updateFields(updated);
                }
            }
            default -> throw new BusinessException(ErrorCode.CONFLICT, "마감된 공고는 수정할 수 없습니다.");
        }
        return toResponse(postingMapper.findById(postingId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyJobPostingResponse> listMine(Long companyUserId) {
        requireCompany(companyUserId);
        return postingMapper.findMine(companyUserId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyJobPostingResponse getMine(Long companyUserId, Long postingId) {
        CompanyJobPosting posting = postingMapper.findById(postingId);
        if (posting == null || !posting.getCompanyUserId().equals(companyUserId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고를 찾을 수 없습니다.");
        }
        return toResponse(posting);
    }

    @Override
    @Transactional
    public CompanyJobPostingResponse close(Long companyUserId, Long postingId) {
        CompanyJobPosting posting = requireOwnedForUpdate(companyUserId, postingId);
        if (!"PUBLISHED".equals(posting.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "게시 중인 공고만 마감할 수 있습니다.");
        }
        postingMapper.markClosed(postingId);
        return toResponse(postingMapper.findById(postingId));
    }

    // ── 공개 게시판 ──

    @Override
    @Transactional(readOnly = true)
    public JobPostingPageResponse search(String keyword, String jobRole, String location,
                                         String employmentType, String careerLevel,
                                         String sort, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        JobPostingSearchCriteria criteria = new JobPostingSearchCriteria(
                blankToNull(keyword),
                blankToNull(jobRole),
                blankToNull(location),
                normalizeOrNull(employmentType, EMPLOYMENT_TYPES),
                normalizeOrNull(careerLevel, CAREER_LEVELS),
                SORTS.contains(sort) ? sort : "latest",
                safePage,
                safeSize,
                safePage * safeSize);
        long total = postingMapper.countPublished(criteria);
        List<CompanyJobPostingResponse> items =
                postingMapper.searchPublished(criteria).stream().map(this::toResponse).toList();
        return new JobPostingPageResponse(items, total, criteria.page(), criteria.size());
    }

    @Override
    @Transactional
    public CompanyJobPostingResponse getPublished(Long postingId) {
        CompanyJobPosting posting = postingMapper.findById(postingId);
        if (posting == null || !"PUBLISHED".equals(posting.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시 중인 공고를 찾을 수 없습니다.");
        }
        postingMapper.increaseViewCount(postingId);
        return toResponse(posting);
    }

    @Override
    public JobBoardAnalyzeResponse analyze(Long userId, Long postingId) {
        CompanyJobPosting posting = postingMapper.findById(postingId);
        if (posting == null || !"PUBLISHED".equals(posting.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시 중인 공고를 찾을 수 없습니다.");
        }
        // 기존 지원 건 생성 파이프라인 재사용 — 공고 본문 텍스트로 케이스 생성 + 추출 큐 등록
        ApplicationCaseFromJobPostingResponse created = applicationCaseService.createFromJobPosting(
                userId,
                new CreateApplicationCaseFromJobPostingRequest(
                        buildPostingText(posting), null, null, "TEXT", Boolean.FALSE));
        return new JobBoardAnalyzeResponse(created.applicationCase().id());
    }

    // ── 관리자 검토 ──

    @Override
    @Transactional(readOnly = true)
    public List<JobPostingReviewRow> reviewQueue() {
        return postingMapper.findReviewQueue();
    }

    @Override
    @Transactional(readOnly = true)
    public JobPostingReviewDetailResponse reviewDetail(Long postingId) {
        CompanyJobPosting posting = postingMapper.findById(postingId);
        if (posting == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고를 찾을 수 없습니다.");
        }
        CompanyJobPostingRevision pending = postingMapper.findPendingRevisionByPostingId(postingId);
        JobPostingUpsertRequest payload = pending == null ? null : readPayload(pending.getPayloadJson());
        return new JobPostingReviewDetailResponse(
                toResponse(posting),
                pending == null ? null : pending.getId(),
                payload);
    }

    @Override
    @Transactional
    public void approveReview(Long adminId, Long postingId) {
        CompanyJobPosting posting = requireExistsForUpdate(postingId);
        CompanyJobPostingRevision pending = postingMapper.findPendingRevisionByPostingId(postingId);

        if (pending != null) {
            // 수정 검토 승인 — 변경본을 본문에 반영(게시 상태 유지)
            JobPostingUpsertRequest payload = readPayload(pending.getPayloadJson());
            CompanyJobPosting updated = toPosting(payload);
            updated.setId(postingId);
            updated.setStatus(posting.getStatus());
            postingMapper.updateFields(updated);
            postingMapper.reviewRevision(pending.getId(), "APPROVED", null, adminId);
            eventPublisher.publishEvent(new JobPostingReviewedEvent(
                    postingId, posting.getCompanyUserId(), posting.getTitle(), true, true, null));
            return;
        }
        if (!"PENDING_REVIEW".equals(posting.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "검토 대기 중인 공고가 아닙니다.");
        }
        postingMapper.markPublished(postingId, adminId);
        eventPublisher.publishEvent(new JobPostingReviewedEvent(
                postingId, posting.getCompanyUserId(), posting.getTitle(), true, false, null));
        eventPublisher.publishEvent(new JobPostingPublishedEvent(postingId));
    }

    @Override
    @Transactional
    public void rejectReview(Long adminId, Long postingId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "반려 사유를 입력해 주세요.");
        }
        String trimmed = reason.strip();
        CompanyJobPosting posting = requireExistsForUpdate(postingId);
        CompanyJobPostingRevision pending = postingMapper.findPendingRevisionByPostingId(postingId);

        if (pending != null) {
            postingMapper.reviewRevision(pending.getId(), "REJECTED", trimmed, adminId);
            eventPublisher.publishEvent(new JobPostingReviewedEvent(
                    postingId, posting.getCompanyUserId(), posting.getTitle(), false, true, trimmed));
            return;
        }
        if (!"PENDING_REVIEW".equals(posting.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "검토 대기 중인 공고가 아닙니다.");
        }
        postingMapper.updateStatus(postingId, "REJECTED", trimmed, adminId);
        eventPublisher.publishEvent(new JobPostingReviewedEvent(
                postingId, posting.getCompanyUserId(), posting.getTitle(), false, false, trimmed));
    }

    // ── 내부 헬퍼 ──

    private CompanyProfile requireCompany(Long userId) {
        CompanyProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "승인된 기업 계정만 사용할 수 있습니다.");
        }
        return profile;
    }

    private CompanyJobPosting requireOwnedForUpdate(Long companyUserId, Long postingId) {
        CompanyJobPosting posting = requireExistsForUpdate(postingId);
        if (!posting.getCompanyUserId().equals(companyUserId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고를 찾을 수 없습니다.");
        }
        return posting;
    }

    private CompanyJobPosting requireExistsForUpdate(Long postingId) {
        CompanyJobPosting posting = postingMapper.findByIdForUpdate(postingId);
        if (posting == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고를 찾을 수 없습니다.");
        }
        return posting;
    }

    /** 요청 → 도메인 변환(선택 필드 정규화 포함). status/id 는 호출부에서 채운다. */
    private CompanyJobPosting toPosting(JobPostingUpsertRequest request) {
        return CompanyJobPosting.builder()
                .title(request.title().strip())
                .jobRole(request.jobRole().strip())
                .employmentType(normalizeOrDefault(request.employmentType(), EMPLOYMENT_TYPES, "FULL_TIME"))
                .careerLevel(normalizeOrDefault(request.careerLevel(), CAREER_LEVELS, "ANY"))
                .careerYearsMin(request.careerYearsMin())
                .careerYearsMax(request.careerYearsMax())
                .educationLevel(normalizeOrDefault(request.educationLevel(), EDUCATION_LEVELS, "ANY"))
                .salaryText(blankToNull(request.salaryText()))
                .salaryNegotiable(Boolean.TRUE.equals(request.salaryNegotiable()))
                .workLocation(blankToNull(request.workLocation()))
                .workHours(blankToNull(request.workHours()))
                .deadlineDate(request.deadlineDate())
                .alwaysOpen(Boolean.TRUE.equals(request.alwaysOpen()))
                .mainTasks(blankToNull(request.mainTasks()))
                .requirements(blankToNull(request.requirements()))
                .preferred(blankToNull(request.preferred()))
                .benefits(blankToNull(request.benefits()))
                .hiringProcess(blankToNull(request.hiringProcess()))
                .headcount(blankToNull(request.headcount()))
                .tagsJson(writeTags(request.tags()))
                .build();
    }

    private CompanyJobPostingResponse toResponse(CompanyJobPosting posting) {
        return CompanyJobPostingResponse.from(posting, readTags(posting.getTagsJson()));
    }

    /** RECOMMENDED_JOB 매칭·지원 건 생성에 쓰는 공고 본문 텍스트(사람인식 섹션 조립). */
    private String buildPostingText(CompanyJobPosting posting) {
        StringJoiner joiner = new StringJoiner("\n\n");
        joiner.add("[" + nullToEmpty(posting.getCompanyName()) + "] " + posting.getTitle());
        joiner.add("직무: " + posting.getJobRole());
        appendSection(joiner, "고용형태", posting.getEmploymentType());
        appendSection(joiner, "근무지역", posting.getWorkLocation());
        appendSection(joiner, "근무시간", posting.getWorkHours());
        appendSection(joiner, "급여", posting.getSalaryText());
        appendSection(joiner, "채용인원", posting.getHeadcount());
        appendSection(joiner, "주요업무", posting.getMainTasks());
        appendSection(joiner, "자격요건", posting.getRequirements());
        appendSection(joiner, "우대사항", posting.getPreferred());
        appendSection(joiner, "복리후생", posting.getBenefits());
        appendSection(joiner, "전형절차", posting.getHiringProcess());
        List<String> tags = readTags(posting.getTagsJson());
        if (!tags.isEmpty()) {
            joiner.add("태그: " + String.join(", ", tags));
        }
        return joiner.toString();
    }

    private static void appendSection(StringJoiner joiner, String label, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(label + ": " + value);
        }
    }

    private String writePayload(JobPostingUpsertRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "변경본 직렬화에 실패했습니다.");
        }
    }

    private JobPostingUpsertRequest readPayload(String json) {
        try {
            return objectMapper.readValue(json, JobPostingUpsertRequest.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "변경본을 읽지 못했습니다.");
        }
    }

    private String writeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(
                    tags.stream().map(String::strip).filter(tag -> !tag.isEmpty()).toList());
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readTags(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private static String normalizeOrDefault(String value, Set<String> allowed, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String upper = value.strip().toUpperCase();
        return allowed.contains(upper) ? upper : defaultValue;
    }

    private static String normalizeOrNull(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.strip().toUpperCase();
        return allowed.contains(upper) ? upper : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
