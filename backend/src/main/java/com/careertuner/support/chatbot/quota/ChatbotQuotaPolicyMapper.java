package com.careertuner.support.chatbot.quota;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatbotQuotaPolicyMapper {

    ChatbotQuotaPolicy findPolicy();

    int updatePolicy(@Param("enabled") boolean enabled,
                     @Param("dailyLimit") int dailyLimit,
                     @Param("updatedBy") Long updatedBy);
}
