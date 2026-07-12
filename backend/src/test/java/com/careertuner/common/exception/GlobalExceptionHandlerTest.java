package com.careertuner.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RequestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void malformedJsonReturnsInvalidInputEnvelope() throws Exception {
        mockMvc.perform(post("/request/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void missingParameterAndHeaderReturnBadRequest() throws Exception {
        mockMvc.perform(get("/request/number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/request/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void invalidParameterTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/request/number").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void unsupportedMethodAndMediaTypeKeepProtocolStatus() throws Exception {
        mockMvc.perform(put("/request/only-get"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(post("/request/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void dbConnectionFailureMapsTo503() {
        var handler = new GlobalExceptionHandler();
        var response = handler.handleDbUnavailable(new DataAccessResourceFailureException("connection refused"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void transientResourceFailureMapsTo503() {
        var response = new GlobalExceptionHandler()
                .handleDbUnavailable(new TransientDataAccessResourceException("communications link failure"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void constraintViolationIsNotTreatedAsOutage() {
        var response = new GlobalExceptionHandler()
                .handleUnexpected(new DataIntegrityViolationException("duplicate key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void genericErrorStays500() {
        var response = new GlobalExceptionHandler().handleUnexpected(new RuntimeException("some bug"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @RestController
    @RequestMapping("/request")
    private static class RequestController {

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        String body(@RequestBody Payload payload) {
            return payload.value();
        }

        @GetMapping("/number")
        int number(@RequestParam int value) {
            return value;
        }

        @GetMapping("/header")
        String header(@RequestHeader("X-Required") String value) {
            return value;
        }

        @GetMapping("/only-get")
        String onlyGet() {
            return "ok";
        }
    }

    private record Payload(String value) {
    }
}
