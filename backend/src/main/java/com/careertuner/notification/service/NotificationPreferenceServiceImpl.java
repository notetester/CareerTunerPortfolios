package com.careertuner.notification.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.notification.domain.NotificationPreference;
import com.careertuner.notification.dto.NotificationPreferenceResponse;
import com.careertuner.notification.dto.NotificationPreferenceUpdateRequest;
import com.careertuner.notification.dto.NotificationRulePreference;
import com.careertuner.notification.mapper.NotificationPreferenceMapper;
import com.careertuner.notification.mapper.PushSubscriptionMapper;
import com.careertuner.notification.push.NotificationCategories;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final NotificationPreferenceMapper preferenceMapper;
    private final PushSubscriptionMapper pushSubscriptionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public NotificationPreferenceResponse get(Long userId) {
        NotificationPreference pref = preferenceMapper.findByUserId(userId);
        Map<String, Boolean> stored = parseCategories(pref != null ? pref.getCategoriesJson() : null);
        Map<String, NotificationRulePreference> storedRules = parseRules(pref != null ? pref.getRulesJson() : null);

        Map<String, Boolean> categories = new LinkedHashMap<>();
        for (String c : NotificationCategories.USER_CATEGORIES) {
            categories.put(c, stored.getOrDefault(c, Boolean.TRUE));
        }
        Map<String, NotificationRulePreference> rules = mergeRuleDefaults(storedRules);

        boolean pushEnabled = pref == null || pref.isPushEnabled();
        boolean emailEnabled = pref == null || pref.isEmailEnabled();
        String qs = pref != null ? pref.getQuietHoursStart() : null;
        String qe = pref != null ? pref.getQuietHoursEnd() : null;
        boolean registered = pushSubscriptionMapper.countByUserId(userId) > 0;

        return new NotificationPreferenceResponse(pushEnabled, emailEnabled, categories, rules, qs, qe, registered);
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse update(Long userId, NotificationPreferenceUpdateRequest request) {
        NotificationPreferenceResponse current = get(userId);

        boolean pushEnabled = request.pushEnabled() != null ? request.pushEnabled() : current.pushEnabled();
        boolean emailEnabled = request.emailEnabled() != null ? request.emailEnabled() : current.emailEnabled();
        String qs = request.quietHoursStart() != null ? request.quietHoursStart() : current.quietHoursStart();
        String qe = request.quietHoursEnd() != null ? request.quietHoursEnd() : current.quietHoursEnd();

        Map<String, Boolean> categories = new LinkedHashMap<>(current.categories());
        if (request.categories() != null) {
            for (String c : NotificationCategories.USER_CATEGORIES) {
                if (request.categories().containsKey(c)) {
                    categories.put(c, Boolean.TRUE.equals(request.categories().get(c)));
                }
            }
        }

        Map<String, NotificationRulePreference> rules = new LinkedHashMap<>(current.rules());
        if (request.rules() != null) {
            for (String type : NotificationCategories.USER_RULE_TYPES) {
                NotificationRulePreference requested = request.rules().get(type);
                if (requested != null) {
                    rules.put(type, rules.getOrDefault(type, NotificationRulePreference.enabledAll()).merge(requested));
                }
            }
        }

        preferenceMapper.upsert(NotificationPreference.builder()
                .userId(userId)
                .pushEnabled(pushEnabled)
                .emailEnabled(emailEnabled)
                .categoriesJson(toJson(categories))
                .rulesJson(toJson(rules))
                .quietHoursStart(qs)
                .quietHoursEnd(qe)
                .build());

        return get(userId);
    }

    private Map<String, Boolean> parseCategories(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Boolean>>() {
            });
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private Map<String, NotificationRulePreference> parseRules(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, NotificationRulePreference>>() {
            });
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private Map<String, NotificationRulePreference> mergeRuleDefaults(Map<String, NotificationRulePreference> stored) {
        Map<String, NotificationRulePreference> rules = new LinkedHashMap<>();
        for (String type : NotificationCategories.USER_RULE_TYPES) {
            rules.put(type, NotificationRulePreference.enabledAll().merge(stored.get(type)));
        }
        return rules;
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
