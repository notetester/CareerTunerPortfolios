package com.careertuner.admin.faq.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.faq.dto.AdminFaqRequest;
import com.careertuner.admin.faq.dto.AdminFaqResponse;
import com.careertuner.admin.faq.mapper.AdminFaqMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminFaqServiceImpl implements AdminFaqService {

    private final AdminFaqMapper faqMapper;

    @Override
    public List<AdminFaqResponse> getFaqs(AuthUser authUser) {
        requireAdmin(authUser);
        return faqMapper.findAll();
    }

    @Override
    @Transactional
    public AdminFaqResponse createFaq(AuthUser authUser, AdminFaqRequest request) {
        requireAdmin(authUser);
        boolean published = request.isPublished() != null && request.isPublished();
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;
        faqMapper.insert(
                toDbCategory(request.category()),
                request.question(),
                request.answer(),
                published,
                sortOrder,
                authUser.id()
        );
        Long newId = faqMapper.lastInsertId();
        return faqMapper.findById(newId);
    }

    @Override
    @Transactional
    public AdminFaqResponse updateFaq(AuthUser authUser, Long id, AdminFaqRequest request) {
        requireAdmin(authUser);
        AdminFaqResponse existing = faqMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "FAQ를 찾을 수 없습니다.");
        }
        boolean published = request.isPublished() != null ? request.isPublished() : existing.isPublished();
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : existing.getSortOrder();
        faqMapper.update(
                id,
                toDbCategory(request.category()),
                request.question(),
                request.answer(),
                published,
                sortOrder
        );
        return faqMapper.findById(id);
    }

    @Override
    @Transactional
    public void deleteFaq(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminFaqResponse existing = faqMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "FAQ를 찾을 수 없습니다.");
        }
        faqMapper.delete(id);
    }

    private void requireAdmin(AuthUser authUser) {
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
    }

    private String toDbCategory(String category) {
        if (category == null) return "general";
        return switch (category) {
            case "일반", "GENERAL", "general" -> "general";
            case "계정", "ACCOUNT", "account" -> "account";
            case "결제", "PAYMENT", "payment" -> "payment";
            case "AI기능", "AI_FEATURE", "ai_feature" -> "ai_feature";
            case "면접", "INTERVIEW", "interview" -> "interview";
            default -> category.toLowerCase();
        };
    }
}
