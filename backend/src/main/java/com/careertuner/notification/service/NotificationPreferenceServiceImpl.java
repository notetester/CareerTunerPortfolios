package com.careertuner.notification.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
        List<String> keywords = parseKeywords(pref != null ? pref.getKeywordsJson() : null);

        boolean pushEnabled = pref == null || pref.isPushEnabled();
        boolean emailEnabled = pref == null || pref.isEmailEnabled();
        String qs = pref != null ? pref.getQuietHoursStart() : null;
        String qe = pref != null ? pref.getQuietHoursEnd() : null;
        boolean registered = pushSubscriptionMapper.countByUserId(userId) > 0;

        return new NotificationPreferenceResponse(pushEnabled, emailEnabled, categories, rules, keywords, qs, qe, registered);
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

        List<String> keywords = request.keywords() != null
                ? normalizeKeywords(request.keywords())
                : current.keywords();

        preferenceMapper.upsert(NotificationPreference.builder()
                .userId(userId)
                .pushEnabled(pushEnabled)
                .emailEnabled(emailEnabled)
                .categoriesJson(toJson(categories))
                .rulesJson(toJson(rules))
                .keywordsJson(toJson(keywords))
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

    private static final int MAX_KEYWORDS = 20;
    private static final int MAX_KEYWORD_LENGTH = 30;

    private List<String> parseKeywords(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return normalizeKeywords(objectMapper.readValue(json, new TypeReference<List<String>>() {
            }));
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    /** 공백 정리·중복 제거·개수/길이 상한. 언급 감지에 그대로 쓰이므로 저장 시점에 정규화한다. */
    private List<String> normalizeKeywords(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String keyword : raw) {
            if (keyword == null) {
                continue;
            }
            String trimmed = keyword.trim();
            if (trimmed.isEmpty() || trimmed.length() > MAX_KEYWORD_LENGTH) {
                continue;
            }
            normalized.add(trimmed);
            if (normalized.size() >= MAX_KEYWORDS) {
                break;
            }
        }
        return List.copyOf(normalized);
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
