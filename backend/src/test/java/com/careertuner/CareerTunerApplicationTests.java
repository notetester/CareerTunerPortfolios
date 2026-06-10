package com.careertuner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.security.JwtTokenProvider;
import com.careertuner.common.web.ApiResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@SpringBootTest
class CareerTunerApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        assertThat(applicationContext.getBeansOfType(ObjectMapper.class)).hasSize(1);
    }

    @Test
    void apiResponseUsesBootManagedJackson3Configuration() {
        JsonNode json = objectMapper.valueToTree(ApiResponse.ok(Map.of("value", "ok")));

        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("data").path("value").asText()).isEqualTo("ok");
        assertThat(json.has("message")).isFalse();
    }

    @Test
    void publicJsonEndpointsRespond() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    void jwtRoundTripStillWorks() {
        String token = jwtTokenProvider.createAccessToken(1L, "user@example.com", "USER");
        AuthUser user = jwtTokenProvider.parseAccessToken(token);

        assertThat(user.id()).isEqualTo(1L);
        assertThat(user.email()).isEqualTo("user@example.com");
        assertThat(user.role()).isEqualTo("USER");
    }
}
