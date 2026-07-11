package com.careertuner.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockFilterChain;

import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

import tools.jackson.databind.ObjectMapper;

class AdminAccountStateFilterTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AdminAccountStateFilter filter = new AdminAccountStateFilter(
            userMapper, new SecurityErrorResponseWriter(new ObjectMapper()));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeAdminWithMatchingClaimContinues() throws Exception {
        authenticate(new AuthUser(7L, "admin@test.dev", "ADMIN"));
        when(userMapper.findById(7L)).thenReturn(user(7L, "ADMIN", "ACTIVE"));
        MockHttpServletResponse response = invoke("/api/admin/me/permissions");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void demotedAdminClaimIsUnauthorizedImmediately() throws Exception {
        authenticate(new AuthUser(7L, "admin@test.dev", "ADMIN"));
        when(userMapper.findById(7L)).thenReturn(user(7L, "USER", "ACTIVE"));
        MockHttpServletResponse response = invoke("/api/admin/users");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void oldUserClaimIsNotAutomaticallyPromoted() throws Exception {
        authenticate(new AuthUser(7L, "user@test.dev", "USER"));
        when(userMapper.findById(7L)).thenReturn(user(7L, "ADMIN", "ACTIVE"));
        MockHttpServletResponse response = invoke("/api/admin/users");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void blockedDeletedOrMissingAccountIsUnauthorized() throws Exception {
        for (String status : List.of("BLOCKED", "DELETED")) {
            authenticate(new AuthUser(7L, "admin@test.dev", "ADMIN"));
            when(userMapper.findById(7L)).thenReturn(user(7L, "ADMIN", status));
            assertThat(invoke("/api/admin/users").getStatus()).isEqualTo(401);
        }
        authenticate(new AuthUser(7L, "admin@test.dev", "ADMIN"));
        when(userMapper.findById(7L)).thenReturn(null);
        assertThat(invoke("/api/admin/users").getStatus()).isEqualTo(401);
    }

    @Test
    void nonAdminPathDoesNotQueryCurrentAdminState() throws Exception {
        authenticate(new AuthUser(7L, "admin@test.dev", "ADMIN"));
        MockHttpServletResponse response = invoke("/api/profile/me");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse invoke(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static void authenticate(AuthUser authUser) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                authUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + authUser.role()))));
    }

    private static User user(Long id, String role, String status) {
        return User.builder().id(id).role(role).status(status).build();
    }
}
