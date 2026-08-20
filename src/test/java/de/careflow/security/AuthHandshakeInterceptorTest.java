package de.careflow.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class AuthHandshakeInterceptorTest {

    private final AuthHandshakeInterceptor interceptor = new AuthHandshakeInterceptor();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsHandshakeWithoutPrincipal() {
        HandshakeAttempt attempt = handshake();

        boolean accepted = interceptor.beforeHandshake(
                attempt.request, attempt.response, new TextWebSocketHandler(), new HashMap<>());

        assertThat(accepted).isFalse();
        assertThat(attempt.response.getServletResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void rejectsAnonymousAuthentication() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
        HandshakeAttempt attempt = handshake();

        boolean accepted = interceptor.beforeHandshake(
                attempt.request, attempt.response, new TextWebSocketHandler(), new HashMap<>());

        assertThat(accepted).isFalse();
        assertThat(attempt.response.getServletResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void acceptsHandshakeWithAuthenticatedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(staffToken());
        HandshakeAttempt attempt = handshake();

        boolean accepted = interceptor.beforeHandshake(
                attempt.request, attempt.response, new TextWebSocketHandler(), new HashMap<>());

        assertThat(accepted).isTrue();
    }

    @Test
    void acceptsHandshakeWithSecurityContextInHttpSession() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(staffToken()));
        HandshakeAttempt attempt = handshake(servletRequest);

        boolean accepted = interceptor.beforeHandshake(
                attempt.request, attempt.response, new TextWebSocketHandler(), new HashMap<>());

        assertThat(accepted).isTrue();
    }

    private static Authentication staffToken() {
        return UsernamePasswordAuthenticationToken.authenticated(
                "weber", "n/a", AuthorityUtils.createAuthorityList("ROLE_PHYSICIAN"));
    }

    private static HandshakeAttempt handshake() {
        return handshake(new MockHttpServletRequest());
    }

    private static HandshakeAttempt handshake(MockHttpServletRequest servletRequest) {
        return new HandshakeAttempt(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()));
    }

    private record HandshakeAttempt(ServletServerHttpRequest request, ServletServerHttpResponse response) {
    }
}
