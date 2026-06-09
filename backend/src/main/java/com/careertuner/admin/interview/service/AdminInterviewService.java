package com.careertuner.admin.interview.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.interview.dto.AdminInterviewSessionDetail;
import com.careertuner.admin.interview.dto.AdminInterviewSessionRow;
import com.careertuner.admin.interview.mapper.AdminInterviewMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.interview.dto.InterviewAnswerResponse;
import com.careertuner.interview.dto.InterviewQuestionResponse;
import com.careertuner.interview.mapper.InterviewMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminInterviewService {

    private static final Set<String> MODES = Set.of(
            "BASIC", "JOB", "PERSONALITY", "PRESSURE", "REAL", "RESUME", "PORTFOLIO", "COMPANY");

    private final AdminInterviewMapper adminInterviewMapper;
    private final InterviewMapper interviewMapper;

    @Transactional(readOnly = true)
    public List<AdminInterviewSessionRow> sessions(AuthUser authUser, String keyword, String mode, int limit) {
        requireAdmin(authUser);
        return adminInterviewMapper.findSessions(blankToNull(keyword), normalizeMode(mode), normalizeLimit(limit));
    }

    @Transactional(readOnly = true)
    public AdminInterviewSessionDetail detail(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminInterviewSessionRow session = adminInterviewMapper.findSession(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 세션을 찾을 수 없습니다.");
        }
        List<InterviewQuestionResponse> questions = interviewMapper.findQuestionsBySessionId(id).stream()
                .map(InterviewQuestionResponse::from)
                .toList();
        List<InterviewAnswerResponse> answers = interviewMapper.findAnswersBySessionId(id).stream()
                .map(InterviewAnswerResponse::from)
                .toList();
        return new AdminInterviewSessionDetail(session, questions, answers, adminInterviewMapper.findReport(id));
    }

    private static void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }

    private static String normalizeMode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "mode 값이 올바르지 않습니다.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
