package com.careertuner.admin.prompt.analytics.service;

import java.util.List;

import com.careertuner.admin.prompt.analytics.dto.AdminAnalyticsPromptResponse;

public interface AdminAnalyticsPromptService {

    List<AdminAnalyticsPromptResponse> list();

    AdminAnalyticsPromptResponse get(String key);
}
