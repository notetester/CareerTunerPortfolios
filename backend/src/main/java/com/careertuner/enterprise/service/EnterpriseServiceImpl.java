package com.careertuner.enterprise.service;

import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.ops.dto.AdminActionLogCreate;
import com.careertuner.admin.ops.mapper.AdminActionLogMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostCategory;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.event.PostPublishedEvent;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.enterprise.domain.EnterpriseAccountApplication;
import com.careertuner.enterprise.domain.EnterpriseJobPolicy;
import com.careertuner.enterprise.domain.EnterpriseJobPosting;
import com.careertuner.enterprise.dto.EnterpriseDtos.ApplicationRequest;
import com.careertuner.enterprise.dto.EnterpriseDtos.ApplicationResponse;
import com.careertuner.enterprise.dto.EnterpriseDtos.ApplicationReviewRequest;
import com.careertuner.enterprise.dto.EnterpriseDtos.JobPolicyResponse;
import com.careertuner.enterprise.dto.EnterpriseDtos.JobRequest;
import com.careertuner.enterprise.dto.EnterpriseDtos.JobResponse;
import com.careertuner.enterprise.dto.EnterpriseDtos.JobReviewRequest;
import com.careertuner.enterprise.dto.EnterpriseDtos.StatusResponse;
import com.careertuner.enterprise.mapper.EnterpriseMapper;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnterpriseServiceImpl implements EnterpriseService {

    private static final int DEFAULT_MAX_ACTIVE_POSTS = 5;

    private final EnterpriseMapper enterpriseMapper;
    private final UserMapper userMapper;
    private final CommunityPostMapper communityPostMapper;
    private final AdminActionLogMapper actionLogMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public StatusResponse myStatus(Long userId) {
        User user = requireUser(userId);
        EnterpriseAccountApplication application = enterpriseMapper.findLatestApplicationByUserId(userId);
        EnterpriseJobPolicy policy = policyOrDefault(userId);
        return new StatusResponse("EMPLOYER".equals(user.getAccountType()), toApplicationResponse(application),
                toPolicyResponse(policy));
    }

    @Override
    @Transactional
    public ApplicationResponse apply(Long userId, ApplicationRequest request) {
        requireUser(userId);
        EnterpriseAccountApplication latest = enterpriseMapper.findLatestApplicationByUserId(userId);
        if (latest != null && "PENDING".equals(latest.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 검토 중인 기업 계정 신청이 있습니다.");
        }
        EnterpriseAccountApplication application = EnterpriseAccountApplication.builder()
                .userId(userId)
                .companyName(requireText(request.companyName(), "회사명을 입력해 주세요."))
                .businessNumber(blankToNull(request.businessNumber()))
                .representativeName(blankToNull(request.representativeName()))
                .contactName(blankToNull(request.contactName()))
                .contactEmail(blankToNull(request.contactEmail()))
                .contactPhone(blankToNull(request.contactPhone()))
                .websiteUrl(blankToNull(request.websiteUrl()))
                .industry(blankToNull(request.industry()))
                .employeeCount(blankToNull(request.employeeCount()))
                .evidenceFileUrl(blankToNull(request.evidenceFileUrl()))
                .requestedPolicyJson(toJson(Map.of(
                        "createRequiresReview", request.createRequiresReview() == null || request.createRequiresReview(),
                        "editRequiresReview", request.editRequiresReview() == null || request.editRequiresReview())))
                .status("PENDING")
                .build();
        enterpriseMapper.insertApplication(application);
        return toApplicationResponse(enterpriseMapper.findApplicationById(application.getId()));
    }

    @Override
    public List<JobResponse> myJobs(Long userId) {
        return enterpriseMapper.findJobsByOwner(userId).stream().map(this::toJobResponse).toList();
    }

    @Override
    @Transactional
    public JobResponse createJob(Long userId, JobRequest request) {
        requireEmployer(userId);
        EnterpriseJobPolicy policy = policyOrDefault(userId);
        if (enterpriseMapper.countActiveJobsByUserId(userId) >= Math.max(1, policy.getMaxActivePosts())) {
            throw new BusinessException(ErrorCode.CONFLICT, "활성 공고 수가 기업 정책 한도에 도달했습니다.");
        }
        EnterpriseJobPosting job = EnterpriseJobPosting.builder()
                .companyUserId(userId)
                .status(policy.isCreateRequiresReview() ? "PENDING_REVIEW" : "PUBLISHED")
                .reviewStatus(policy.isCreateRequiresReview() ? "PENDING" : "APPROVED")
                .visibility("PUBLIC")
                .build();
        applyJobFields(job, request);
        enterpriseMapper.insertJob(job);
        if ("PUBLISHED".equals(job.getStatus())) {
            publishCommunityPost(job.getId(), userId);
        }
        return toJobResponse(enterpriseMapper.findJobById(job.getId()));
    }

    @Override
    @Transactional
    public JobResponse updateJob(Long userId, Long jobId, JobRequest request) {
        requireEmployer(userId);
        EnterpriseJobPosting job = requireJob(jobId);
        if (!userId.equals(job.getCompanyUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 기업의 공고만 수정할 수 있습니다.");
        }
        EnterpriseJobPolicy policy = policyOrDefault(userId);
        if (policy.isEditRequiresReview() && "PUBLISHED".equals(job.getStatus())) {
            enterpriseMapper.updateJobPendingRevision(jobId, toJson(request), "PENDING", "수정안 검토 대기");
            return toJobResponse(enterpriseMapper.findJobById(jobId));
        }
        applyJobFields(job, request);
        job.setStatus(policy.isEditRequiresReview() ? "PENDING_REVIEW" : "PUBLISHED");
        job.setReviewStatus(policy.isEditRequiresReview() ? "PENDING" : "APPROVED");
        job.setReviewMemo(policy.isEditRequiresReview() ? "수정 검토 대기" : null);
        enterpriseMapper.updateJob(job);
        if ("PUBLISHED".equals(job.getStatus())) {
            publishCommunityPost(job.getId(), userId);
        }
        return toJobResponse(enterpriseMapper.findJobById(jobId));
    }

    @Override
    public List<JobResponse> publicJobs(String keyword, int limit) {
        return enterpriseMapper.findPublicJobs(blankToNull(keyword), Math.max(1, Math.min(limit, 50))).stream()
                .map(this::toJobResponse)
                .toList();
    }

    @Override
    public List<ApplicationResponse> adminApplications(AuthUser authUser, String status, String keyword, int limit) {
        AdminAccess.requireAdmin(authUser);
        return enterpriseMapper.findApplications(blankToNull(status), blankToNull(keyword), Math.max(1, Math.min(limit, 200)))
                .stream().map(this::toApplicationResponse).toList();
    }

    @Override
    @Transactional
    public ApplicationResponse adminReviewApplication(AuthUser authUser, Long applicationId,
                                                      ApplicationReviewRequest request) {
        AdminAccess.requireAdmin(authUser);
        EnterpriseAccountApplication application = requireApplication(applicationId);
        String status = normalizeDecision(request.status());
        enterpriseMapper.reviewApplication(applicationId, status, blankToNull(request.reviewMemo()), authUser.id());
        if ("APPROVED".equals(status)) {
            boolean trusted = Boolean.TRUE.equals(request.trusted());
            boolean createRequiresReview = request.createRequiresReview() == null || request.createRequiresReview();
            boolean editRequiresReview = request.editRequiresReview() == null || request.editRequiresReview();
            int maxActivePosts = request.maxActivePosts() == null ? DEFAULT_MAX_ACTIVE_POSTS
                    : Math.max(1, Math.min(request.maxActivePosts(), 100));
            userMapper.updateEnterpriseAccount(application.getUserId(), "EMPLOYER", trusted);
            enterpriseMapper.upsertPolicy(application.getUserId(), trusted, createRequiresReview, editRequiresReview,
                    maxActivePosts, authUser.id(), blankToNull(request.reviewMemo()));
        }
        logAdmin(authUser.id(), application.getUserId(), "ENTERPRISE_APPLICATION_" + status,
                "ENTERPRISE_APPLICATION", Map.of("applicationId", applicationId, "status", status),
                request.reviewMemo());
        return toApplicationResponse(enterpriseMapper.findApplicationById(applicationId));
    }

    @Override
    public List<JobResponse> adminJobs(AuthUser authUser, String status, String keyword, int limit) {
        AdminAccess.requireAdmin(authUser);
        return enterpriseMapper.findAdminJobs(blankToNull(status), blankToNull(keyword), Math.max(1, Math.min(limit, 200)))
                .stream().map(this::toJobResponse).toList();
    }

    @Override
    @Transactional
    public JobResponse adminReviewJob(AuthUser authUser, Long jobId, JobReviewRequest request) {
        AdminAccess.requireAdmin(authUser);
        EnterpriseJobPosting job = requireJob(jobId);
        String action = normalizeJobAction(request.action());
        if ("APPROVE".equals(action)) {
            applyPendingRevision(job);
            job.setStatus("PUBLISHED");
            job.setReviewStatus("APPROVED");
            job.setReviewMemo(blankToNull(request.reviewMemo()));
            job.setReviewedBy(authUser.id());
            job.setApprovedBy(authUser.id());
            job.setPendingRevisionJson(null);
            enterpriseMapper.updateJob(job);
            publishCommunityPost(jobId, job.getCompanyUserId());
        } else {
            job.setStatus(job.getCommunityPostId() == null ? "REJECTED" : job.getStatus());
            job.setReviewStatus("REJECTED");
            job.setReviewMemo(blankToNull(request.reviewMemo()));
            job.setReviewedBy(authUser.id());
            job.setPendingRevisionJson(null);
            enterpriseMapper.updateJob(job);
        }
        logAdmin(authUser.id(), job.getCompanyUserId(), "ENTERPRISE_JOB_" + action, "ENTERPRISE_JOB",
                Map.of("jobId", jobId, "action", action), request.reviewMemo());
        return toJobResponse(enterpriseMapper.findJobById(jobId));
    }

    private void applyPendingRevision(EnterpriseJobPosting job) {
        if (job.getPendingRevisionJson() == null || job.getPendingRevisionJson().isBlank()) {
            return;
        }
        try {
            applyJobFields(job, objectMapper.readValue(job.getPendingRevisionJson(), JobRequest.class));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "검토 중인 수정안 JSON을 읽을 수 없습니다.");
        }
    }

    private void publishCommunityPost(Long jobId, Long userId) {
        EnterpriseJobPosting job = requireJob(jobId);
        CommunityPost post = CommunityPost.builder()
                .id(job.getCommunityPostId())
                .userId(userId)
                .category(PostCategory.RECOMMENDED_JOB.name())
                .title(job.getTitle())
                .content(toCommunityContent(job))
                .companyName(job.getCompanyName())
                .jobTitle(job.getPositionTitle())
                .status(PostStatus.PUBLISHED.name())
                .tagsJson(toJson(List.of(
                        job.getJobCategory() == null ? "채용공고" : job.getJobCategory(),
                        job.getEmploymentType() == null ? "고용형태협의" : job.getEmploymentType(),
                        job.getWorkLocation() == null ? "지역협의" : job.getWorkLocation())))
                .anonymous(false)
                .build();
        if (job.getCommunityPostId() == null) {
            communityPostMapper.insert(post);
            enterpriseMapper.updateCommunityPostId(jobId, post.getId());
            eventPublisher.publishEvent(new PostPublishedEvent(post.getId()));
        } else {
            communityPostMapper.update(post);
        }
    }

    private String toCommunityContent(EnterpriseJobPosting job) {
        return """
                ### 주요 업무
                %s

                ### 자격 요건
                %s

                ### 우대 사항
                %s

                ### 근무 조건
                - 고용형태: %s
                - 경력: %s
                - 학력: %s
                - 급여: %s
                - 근무지: %s
                - 근무시간: %s
                - 모집인원: %s

                ### 지원
                - 접수 마감: %s
                - 지원 링크: %s
                - 문의: %s
                """.formatted(
                nullToDash(job.getDuties()),
                nullToDash(job.getQualifications()),
                nullToDash(job.getPreferred()),
                nullToDash(job.getEmploymentType()),
                nullToDash(job.getExperienceLevel()),
                nullToDash(job.getEducationLevel()),
                job.getSalaryText() != null ? job.getSalaryText() : salaryRange(job),
                nullToDash(job.getWorkLocation()),
                nullToDash(job.getWorkSchedule()),
                nullToDash(job.getHeadcount()),
                job.getApplicationEndAt() == null ? "상시/협의" : job.getApplicationEndAt().toLocalDate().toString(),
                nullToDash(job.getApplyUrl()),
                nullToDash(job.getContactEmail() != null ? job.getContactEmail() : job.getContactPhone()));
    }

    private void applyJobFields(EnterpriseJobPosting job, JobRequest request) {
        job.setCompanyName(requireText(request.companyName(), "회사명을 입력해 주세요."));
        job.setTitle(requireText(request.title(), "공고 제목을 입력해 주세요."));
        job.setPositionTitle(requireText(request.positionTitle(), "모집 포지션을 입력해 주세요."));
        job.setJobCategory(blankToNull(request.jobCategory()));
        job.setSpecialtiesJson(toJson(request.specialties() == null ? List.of() : request.specialties()));
        job.setDuties(requireText(request.duties(), "주요 업무를 입력해 주세요."));
        job.setQualifications(blankToNull(request.qualifications()));
        job.setPreferred(blankToNull(request.preferred()));
        job.setBenefits(blankToNull(request.benefits()));
        job.setEmploymentType(blankToNull(request.employmentType()));
        job.setExperienceLevel(blankToNull(request.experienceLevel()));
        job.setEducationLevel(blankToNull(request.educationLevel()));
        job.setSalaryType(blankToNull(request.salaryType()));
        job.setSalaryMin(request.salaryMin());
        job.setSalaryMax(request.salaryMax());
        job.setSalaryText(blankToNull(request.salaryText()));
        job.setWorkLocation(blankToNull(request.workLocation()));
        job.setWorkSchedule(blankToNull(request.workSchedule()));
        job.setHeadcount(blankToNull(request.headcount()));
        job.setApplicationStartAt(request.applicationStartAt());
        job.setApplicationEndAt(request.applicationEndAt());
        job.setApplyUrl(blankToNull(request.applyUrl()));
        job.setContactEmail(blankToNull(request.contactEmail()));
        job.setContactPhone(blankToNull(request.contactPhone()));
        job.setVisibility(blankToNull(request.visibility()) == null ? "PUBLIC" : request.visibility().trim().toUpperCase());
    }

    private EnterpriseJobPolicy policyOrDefault(Long userId) {
        EnterpriseJobPolicy policy = enterpriseMapper.findPolicyByUserId(userId);
        if (policy != null) {
            return policy;
        }
        User user = userMapper.findById(userId);
        boolean trusted = user != null && user.isEnterpriseTrusted();
        return EnterpriseJobPolicy.builder()
                .userId(userId)
                .trusted(trusted)
                .createRequiresReview(true)
                .editRequiresReview(true)
                .maxActivePosts(DEFAULT_MAX_ACTIVE_POSTS)
                .build();
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return user;
    }

    private void requireEmployer(Long userId) {
        User user = requireUser(userId);
        if (!"EMPLOYER".equals(user.getAccountType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "승인된 기업 계정만 공고를 등록할 수 있습니다.");
        }
    }

    private EnterpriseAccountApplication requireApplication(Long id) {
        EnterpriseAccountApplication application = enterpriseMapper.findApplicationById(id);
        if (application == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "기업 계정 신청을 찾을 수 없습니다.");
        }
        return application;
    }

    private EnterpriseJobPosting requireJob(Long id) {
        EnterpriseJobPosting job = enterpriseMapper.findJobById(id);
        if (job == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공고를 찾을 수 없습니다.");
        }
        return job;
    }

    private String normalizeDecision(String value) {
        String status = value == null ? "" : value.trim().toUpperCase();
        if (!status.equals("APPROVED") && !status.equals("REJECTED")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "승인 또는 반려 상태를 입력해 주세요.");
        }
        return status;
    }

    private String normalizeJobAction(String value) {
        String action = value == null ? "" : value.trim().toUpperCase();
        if (!action.equals("APPROVE") && !action.equals("REJECT")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "APPROVE 또는 REJECT 액션을 입력해 주세요.");
        }
        return action;
    }

    private ApplicationResponse toApplicationResponse(EnterpriseAccountApplication a) {
        if (a == null) {
            return null;
        }
        return new ApplicationResponse(a.getId(), a.getUserId(), a.getUserEmail(), a.getUserName(),
                a.getCompanyName(), a.getBusinessNumber(), a.getRepresentativeName(), a.getContactName(),
                a.getContactEmail(), a.getContactPhone(), a.getWebsiteUrl(), a.getIndustry(), a.getEmployeeCount(),
                a.getEvidenceFileUrl(), a.getStatus(), a.getReviewMemo(), a.getReviewedBy(), a.getReviewedAt(),
                a.getCreatedAt(), a.getUpdatedAt());
    }

    private JobPolicyResponse toPolicyResponse(EnterpriseJobPolicy p) {
        return new JobPolicyResponse(p.isTrusted(), p.isCreateRequiresReview(), p.isEditRequiresReview(),
                p.getMaxActivePosts());
    }

    private JobResponse toJobResponse(EnterpriseJobPosting j) {
        if (j == null) {
            return null;
        }
        return new JobResponse(j.getId(), j.getCompanyUserId(), j.getOwnerEmail(), j.getOwnerName(),
                j.getCompanyName(), j.getTitle(), j.getPositionTitle(), j.getJobCategory(),
                listFromJson(j.getSpecialtiesJson()), j.getDuties(), j.getQualifications(), j.getPreferred(),
                j.getBenefits(), j.getEmploymentType(), j.getExperienceLevel(), j.getEducationLevel(),
                j.getSalaryType(), j.getSalaryMin(), j.getSalaryMax(), j.getSalaryText(), j.getWorkLocation(),
                j.getWorkSchedule(), j.getHeadcount(), j.getApplicationStartAt(), j.getApplicationEndAt(),
                j.getApplyUrl(), j.getContactEmail(), j.getContactPhone(), j.getVisibility(), j.getStatus(),
                j.getReviewStatus(), j.getReviewMemo(), j.getPendingRevisionJson() != null,
                j.getCommunityPostId(), j.getApprovedAt(), j.getReviewedAt(), j.getCreatedAt(), j.getUpdatedAt());
    }

    @SuppressWarnings("unchecked")
    private List<String> listFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Object value = objectMapper.readValue(json, Object.class);
            if (value instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "JSON으로 저장할 수 없는 요청입니다.");
        }
    }

    private String requireText(String value, String message) {
        String text = blankToNull(value);
        if (text == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        return text;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String salaryRange(EnterpriseJobPosting job) {
        if (job.getSalaryMin() == null && job.getSalaryMax() == null) {
            return "협의";
        }
        return "%s ~ %s".formatted(
                job.getSalaryMin() == null ? "하한 협의" : job.getSalaryMin(),
                job.getSalaryMax() == null ? "상한 협의" : job.getSalaryMax());
    }

    private void logAdmin(Long actorId, Long targetUserId, String action, String targetType,
                          Object afterValue, String reason) {
        actionLogMapper.insert(new AdminActionLogCreate(actorId, targetUserId, action, targetType,
                null, toJson(afterValue), blankToNull(reason), null, null));
    }
}
