package com.careertuner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.security.JwtTokenProvider;
import com.careertuner.common.web.ApiResponse;
import com.zaxxer.hikari.HikariDataSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class CareerTunerApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DataSource dataSource;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void contextLoads() {
        assertThat(applicationContext.getBeansOfType(ObjectMapper.class)).hasSize(1);
    }

    @Test
    void testContextUsesBoundedConnectionPool() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        assertThat(hikariDataSource.getMaximumPoolSize()).isEqualTo(2);
        assertThat(hikariDataSource.getMinimumIdle()).isZero();
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

        mockMvc.perform(get("/api/auth/oauth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.google").isBoolean())
                .andExpect(jsonPath("$.data.kakao").isBoolean())
                .andExpect(jsonPath("$.data.naver").isBoolean());
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
