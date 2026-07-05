package com.careertuner.community.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.mapper.ReactionSettingMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 리액션 유지/해지 설정 — 게시글이 '수정'되었을 때 내 리액션을 유지(keep)할지 해지(release)할지.
 * 저장소는 user_privacy_policy.policy_json 의 별도 최상위 키 "reactionRetention"
 * (privacy 코어의 relations 키와 충돌하지 않는 커뮤니티 소유 키 — privacy 코어를 거치지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReactionSettingServiceImpl implements ReactionSettingService {

    /** 설정 대상 리액션 키(소문자 고정 — release 삭제 쿼리의 LOWER(reaction_type) 와 일치). */
    static final List<String> RETENTION_KEYS =
            List.of("like", "dislike", "recommend", "disrecommend", "bookmark");
    static final String KEEP = "keep";
    static final String RELEASE = "release";

    private final ReactionSettingMapper settingMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, String> getRetentionSettings(Long userId) {
        Map<String, String> stored = parse(settingMapper.findReactionRetentionJson(userId));
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : RETENTION_KEYS) {
            result.put(key, RELEASE.equals(stored.get(key)) ? RELEASE : KEEP); // 기본 keep
        }
        return result;
    }

    @Override
    @Transactional
    public Map<String, String> updateRetentionSettings(Long userId, Map<String, String> settings) {
        if (settings == null || settings.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경할 설정이 없습니다.");
        }
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            if (!RETENTION_KEYS.contains(entry.getKey())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 설정 키입니다: " + entry.getKey());
            }
            if (!KEEP.equals(entry.getValue()) && !RELEASE.equals(entry.getValue())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "설정 값은 keep 또는 release 만 허용됩니다.");
            }
        }
        // 부분 갱신 — 기존 값 위에 보낸 키만 덮어쓴 전체 객체를 upsert(다른 최상위 키는 JSON_SET 으로 보존)
        Map<String, String> merged = getRetentionSettings(userId);
        merged.putAll(settings);
        try {
            settingMapper.upsertReactionRetentionJson(userId, objectMapper.writeValueAsString(merged));
        } catch (Exception e) {
            log.error("리액션 설정 저장 실패 userId={}", userId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "설정 저장에 실패했습니다.");
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parse(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("reactionRetention 파싱 실패 — 기본값 사용: {}", e.getMessage());
            return Map.of();
        }
    }
}
