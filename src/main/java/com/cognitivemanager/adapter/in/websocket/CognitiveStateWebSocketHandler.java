package com.cognitivemanager.adapter.in.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time cognitive state push notifications.
 *
 * Maintains a thread-safe registry of active WebSocket sessions keyed by
 * {@code developerId}. The notification adapter
 * ({@link com.cognitivemanager.adapter.out.notification.WebSocketNotificationAdapter})
 * calls {@link #sendToUser} to push JSON payloads.
 *
 * Connection URL: {@code ws://host:8080/ws/cognitive/{developerId}}
 *
 * Per RNF01: notifications are delivered in &lt;5ms from the WebSocket server.
 * The domain engine computes the result; this handler only serializes and sends.
 */
@Component
public class CognitiveStateWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CognitiveStateWebSocketHandler.class);

    /** Thread-safe map: developerId → active WebSocket session */
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String developerId = extractDeveloperIdFromSession(session);
        activeSessions.put(developerId, session);
        log.info("WebSocket connected: developerId={}, sessionId={}", developerId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String developerId = extractDeveloperIdFromSession(session);
        activeSessions.remove(developerId);
        log.info("WebSocket disconnected: developerId={}, status={}", developerId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String developerId = extractDeveloperIdFromSession(session);
        log.error("WebSocket transport error: developerId={}", developerId, exception);
        activeSessions.remove(developerId);
    }

    /**
     * Sends a JSON payload to the specified developer's WebSocket session.
     * No-ops gracefully if the developer is not connected.
     *
     * @param developerId Target developer
     * @param jsonPayload Serialized JSON string to send
     */
    public void sendToUser(String developerId, String jsonPayload) {
        WebSocketSession session = activeSessions.get(developerId);
        if (session == null || !session.isOpen()) {
            log.debug("WebSocket not available for developerId={} — notification dropped", developerId);
            return;
        }
        try {
            synchronized (session) { // Prevent concurrent writes on the same session
                session.sendMessage(new TextMessage(jsonPayload));
            }
            log.debug("WebSocket message sent to developerId={}: {} chars", developerId, jsonPayload.length());
        } catch (IOException e) {
            log.error("Failed to send WebSocket message to developerId={}", developerId, e);
            activeSessions.remove(developerId);
        }
    }

    /**
     * Returns {@code true} if the developer has an active WebSocket connection.
     */
    public boolean isConnected(String developerId) {
        WebSocketSession session = activeSessions.get(developerId);
        return session != null && session.isOpen();
    }

    public int getActiveConnectionCount() {
        return (int) activeSessions.values().stream().filter(WebSocketSession::isOpen).count();
    }

    /**
     * Extracts the developerId from the WebSocket session URI.
     * Expects URI pattern: /ws/cognitive/{developerId}
     *
     * Falls back to the Spring session ID if the URI cannot be parsed.
     */
    private String extractDeveloperIdFromSession(WebSocketSession session) {
        try {
            String uriPath = session.getUri() != null ? session.getUri().getPath() : "";
            String[] segments = uriPath.split("/");
            return segments.length > 0 ? segments[segments.length - 1] : session.getId();
        } catch (Exception e) {
            return session.getId();
        }
    }
}
