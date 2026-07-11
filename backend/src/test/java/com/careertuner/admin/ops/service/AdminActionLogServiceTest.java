package com.careertuner.admin.ops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.admin.ops.dto.AdminActionLogCreate;
import com.careertuner.admin.ops.mapper.AdminActionLogMapper;
import com.careertuner.common.security.AuthUser;

import tools.jackson.databind.ObjectMapper;

class AdminActionLogServiceTest {

    private final AdminActionLogMapper mapper = mock(AdminActionLogMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdminActionLogService service = new AdminActionLogService(mapper, objectMapper);

    @Test
    void plainStringValuesAreSerializedAsValidJsonStrings() throws Exception {
        service.record(admin(), 20L, "CORRECTION_MEMO_UPDATED", "CORRECTION",
                "123", "새 메모", "첨삭 운영 메모 수정");

        AdminActionLogCreate log = capturedLog();
        assertThat(log.beforeValue()).isEqualTo("\"123\"");
        assertThat(log.afterValue()).isEqualTo("\"새 메모\"");
        assertThat(objectMapper.readTree(log.beforeValue()).isTextual()).isTrue();
        assertThat(objectMapper.readTree(log.beforeValue()).asText()).isEqualTo("123");
        assertThat(objectMapper.readTree(log.afterValue()).asText()).isEqualTo("새 메모");
    }

    @Test
    void structuredValuesAndExistingJsonStringsRemainJsonStructures() throws Exception {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("postId", 10L);
        before.put("status", "PUBLISHED");

        service.record(admin(), 20L, "COMMUNITY_POST_HIDDEN", "COMMUNITY_POST",
                before, "{\"postId\":10,\"status\":\"HIDDEN\"}", null);

        AdminActionLogCreate log = capturedLog();
        assertThat(objectMapper.readTree(log.beforeValue()))
                .isEqualTo(objectMapper.readTree("{\"postId\":10,\"status\":\"PUBLISHED\"}"));
        assertThat(objectMapper.readTree(log.afterValue()))
                .isEqualTo(objectMapper.readTree("{\"postId\":10,\"status\":\"HIDDEN\"}"));
    }

    private AdminActionLogCreate capturedLog() {
        ArgumentCaptor<AdminActionLogCreate> captor = ArgumentCaptor.forClass(AdminActionLogCreate.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }

    private static AuthUser admin() {
        return new AuthUser(1L, "admin@example.com", "SUPER_ADMIN");
    }
}
