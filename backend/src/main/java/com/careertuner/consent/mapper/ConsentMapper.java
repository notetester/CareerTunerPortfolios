package com.careertuner.consent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.auth.domain.UserConsent;
import com.careertuner.consent.dto.ConsentView;

@Mapper
public interface ConsentMapper {

    List<ConsentView> findByUserId(Long userId);

    ConsentView findLatest(@Param("userId") Long userId, @Param("consentType") String consentType);

    void insert(UserConsent consent);

    List<ConsentView> findAdminConsents(@Param("keyword") String keyword,
                                        @Param("consentType") String consentType,
                                        @Param("limit") int limit);
}
