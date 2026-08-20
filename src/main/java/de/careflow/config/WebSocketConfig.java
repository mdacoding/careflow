package de.careflow.config;

import de.careflow.realtime.CareflowSocketHandler;
import de.careflow.security.AuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CareflowSocketHandler handler;

    public WebSocketConfig(CareflowSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/ws")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .addInterceptors(new HttpSessionHandshakeInterceptor(), new AuthHandshakeInterceptor());
    }
}
