package com.careertuner.enterprise.service;

import java.util.List;

import com.careertuner.common.security.AuthUser;
import com.careertuner.enterprise.dto.EnterpriseDtos.ApplicationRequest;
import com.careertuner.enterprise.dto.EnterpriseDtos.ApplicationResponse;
import com.careertuner.enterprise.dto.EnterpriseDtos.ApplicationReviewRequest;
import com.careertuner.enterprise.dto.EnterpriseDtos.JobRequest;
import com.careertuner.enterprise.dto.EnterpriseDtos.JobResponse;
import com.careertuner.enterprise.dto.EnterpriseDtos.JobReviewRequest;
import com.careertuner.enterprise.dto.EnterpriseDtos.StatusResponse;

public interface EnterpriseService {

    StatusResponse myStatus(Long userId);

    ApplicationResponse apply(Long userId, ApplicationRequest request);

    List<JobResponse> myJobs(Long userId);

    JobResponse createJob(Long userId, JobRequest request);

    JobResponse updateJob(Long userId, Long jobId, JobRequest request);

    List<JobResponse> publicJobs(String keyword, int limit);

    List<ApplicationResponse> adminApplications(AuthUser authUser, String status, String keyword, int limit);

    ApplicationResponse adminReviewApplication(AuthUser authUser, Long applicationId, ApplicationReviewRequest request);

    List<JobResponse> adminJobs(AuthUser authUser, String status, String keyword, int limit);

    JobResponse adminReviewJob(AuthUser authUser, Long jobId, JobReviewRequest request);
}
