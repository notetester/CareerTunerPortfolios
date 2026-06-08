package com.careertuner.applicationcase.service;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.mapper.JobPostingMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApplicationCaseAccessService {

    private final ApplicationCaseMapper applicationCaseMapper;
    private final JobPostingMapper jobPostingMapper;

    public ApplicationCase requireOwned(Long userId, Long applicationCaseId) {
        ApplicationCase applicationCase = applicationCaseMapper.findApplicationCaseByIdAndUserId(applicationCaseId, userId);
        if (applicationCase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
        }
        return applicationCase;
    }

    public String sourceTextRequired(Long applicationCaseId) {
        String text = sourceText(applicationCaseId);
        if (isBlank(text)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공고문을 먼저 등록해 주세요.");
        }
        return text;
    }

    public String sourceText(Long applicationCaseId) {
        JobPosting jobPosting = jobPostingMapper.findLatestJobPostingByCaseId(applicationCaseId);
        if (jobPosting == null) {
            return "";
        }
        return defaultString(jobPosting.getExtractedText(), jobPosting.getOriginalText());
    }

    private static String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
