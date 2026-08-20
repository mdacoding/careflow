package de.careflow.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class CareflowSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper;

    public CareflowSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void publish(String type, String patientId, String orderId, String message) {
        Event event = new Event(type, patientId, orderId, message, Instant.now().toString());
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (IOException ex) {
            return;
        }
        TextMessage payload = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            synchronized (session) {
                try {
                    session.sendMessage(payload);
                } catch (IOException ignored) {
                    sessions.remove(session);
                }
            }
        }
    }

    public record Event(String type, String patientId, String orderId, String message, String at) {
    }
}
