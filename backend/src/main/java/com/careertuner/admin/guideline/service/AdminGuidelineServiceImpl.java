package com.careertuner.admin.guideline.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.guideline.dto.AdminGuidelineRequest;
import com.careertuner.admin.guideline.dto.AdminGuidelineResponse;
import com.careertuner.admin.guideline.mapper.AdminGuidelineMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminGuidelineServiceImpl implements AdminGuidelineService {

    private final AdminGuidelineMapper guidelineMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<AdminGuidelineResponse> getGuidelines(AuthUser authUser) {
        requireAdmin(authUser);
        return guidelineMapper.findAll();
    }

    @Override
    public AdminGuidelineResponse getGuideline(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminGuidelineResponse g = guidelineMapper.findById(id);
        if (g == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "가이드라인을 찾을 수 없습니다.");
        }
        return g;
    }

    @Override
    public AdminGuidelineResponse getPublished(AuthUser authUser) {
        requireAdmin(authUser);
        return guidelineMapper.findPublished();
    }

    @Override
    @Transactional
    public AdminGuidelineResponse createGuideline(AuthUser authUser, AdminGuidelineRequest request) {
        requireAdmin(authUser);
        String status = "DRAFT";
        boolean setPublishedAt = false;

        guidelineMapper.insert(
            request.versionLabel(),
            request.summary(),
            request.lede(),
            toJson(request.oks()),
            toJson(request.nos()),
            toJson(request.rules()),
            toJson(request.params()),
            status,
            request.enforceType() != null ? request.enforceType() : "IMMEDIATE",
            request.scheduledAt(),
            authUser.id(),
            setPublishedAt
        );
        Long newId = guidelineMapper.lastInsertId();
        return guidelineMapper.findById(newId);
    }

    @Override
    @Transactional
    public AdminGuidelineResponse updateGuideline(AuthUser authUser, Long id, AdminGuidelineRequest request) {
        requireAdmin(authUser);
        AdminGuidelineResponse existing = guidelineMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "가이드라인을 찾을 수 없습니다.");
        }

        String status = existing.getStatus();
        boolean setPublishedAt = false;

        guidelineMapper.update(
            id,
            request.versionLabel() != null ? request.versionLabel() : existing.getVersionLabel(),
            request.summary() != null ? request.summary() : existing.getSummary(),
            request.lede() != null ? request.lede() : existing.getLede(),
            request.oks() != null ? toJson(request.oks()) : existing.getOksJson(),
            request.nos() != null ? toJson(request.nos()) : existing.getNosJson(),
            request.rules() != null ? toJson(request.rules()) : existing.getRulesJson(),
            request.params() != null ? toJson(request.params()) : existing.getParamsJson(),
            status,
            request.enforceType() != null ? request.enforceType() : existing.getEnforceType(),
            request.scheduledAt(),
            setPublishedAt
        );
        return guidelineMapper.findById(id);
    }

    @Override
    @Transactional
    public AdminGuidelineResponse publishGuideline(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminGuidelineResponse existing = guidelineMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "가이드라인을 찾을 수 없습니다.");
        }

        // 기존 PUBLISHED 버전을 ARCHIVED로 변경
        AdminGuidelineResponse currentPublished = guidelineMapper.findPublished();
        if (currentPublished != null && !currentPublished.getId().equals(id)) {
            guidelineMapper.update(
                currentPublished.getId(),
                currentPublished.getVersionLabel(),
                currentPublished.getSummary(),
                currentPublished.getLede(),
                currentPublished.getOksJson(),
                currentPublished.getNosJson(),
                currentPublished.getRulesJson(),
                currentPublished.getParamsJson(),
                "ARCHIVED",
                currentPublished.getEnforceType(),
                currentPublished.getScheduledAt(),
                false
            );
        }

        guidelineMapper.update(
            id,
            existing.getVersionLabel(),
            existing.getSummary(),
            existing.getLede(),
            existing.getOksJson(),
            existing.getNosJson(),
            existing.getRulesJson(),
            existing.getParamsJson(),
            "PUBLISHED",
            existing.getEnforceType(),
            existing.getScheduledAt(),
            true
        );
        return guidelineMapper.findById(id);
    }

    @Override
    @Transactional
    public void deleteGuideline(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminGuidelineResponse existing = guidelineMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "가이드라인을 찾을 수 없습니다.");
        }
        if ("PUBLISHED".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "게시 중인 가이드라인은 삭제할 수 없습니다.");
        }
        guidelineMapper.delete(id);
    }

    private void requireAdmin(AuthUser authUser) {
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "JSON 변환에 실패했습니다.");
        }
    }
}
