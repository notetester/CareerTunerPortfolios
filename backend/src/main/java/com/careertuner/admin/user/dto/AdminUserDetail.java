package com.careertuner.admin.user.dto;

import java.util.List;

public record AdminUserDetail(
        AdminUserRow user,
        List<AdminUserLoginHistoryRow> loginHistory,
        List<AdminUserStatusHistoryRow> statusHistory,
        List<AdminUserConsentRow> consents,
        List<AdminUserEmailVerificationRow> emailVerifications,
        List<AdminUserRefreshTokenRow> refreshTokens,
        List<AdminUserAiUsageRow> aiUsage,
        AdminUserProfileSnapshot profile) {
}
