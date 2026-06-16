package com.careertuner.admin.faq.service;

import java.util.List;

import com.careertuner.admin.faq.dto.AdminFaqRequest;
import com.careertuner.admin.faq.dto.AdminFaqResponse;
import com.careertuner.common.security.AuthUser;

public interface AdminFaqService {

    List<AdminFaqResponse> getFaqs(AuthUser authUser);

    AdminFaqResponse createFaq(AuthUser authUser, AdminFaqRequest request);

    AdminFaqResponse updateFaq(AuthUser authUser, Long id, AdminFaqRequest request);

    void deleteFaq(AuthUser authUser, Long id);
}
