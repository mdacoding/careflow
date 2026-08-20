package de.careflow.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Aborts the WebSocket handshake unless a signed-in principal is on the request
 * thread or in the HTTP session copied by {@code HttpSessionHandshakeInterceptor}.
 */
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (isAuthenticated(currentAuthentication(request))) {
            return true;
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Handshake already accepted or rejected in beforeHandshake.
    }

    private static Authentication currentAuthentication(ServerHttpRequest request) {
        Authentication fromHolder = SecurityContextHolder.getContext().getAuthentication();
        if (isAuthenticated(fromHolder)) {
            return fromHolder;
        }
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return fromHolder;
        }
        HttpSession session = servletRequest.getServletRequest().getSession(false);
        if (session == null) {
            return fromHolder;
        }
        Object stored = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (stored instanceof SecurityContext context) {
            return context.getAuthentication();
        }
        return fromHolder;
    }

    static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
