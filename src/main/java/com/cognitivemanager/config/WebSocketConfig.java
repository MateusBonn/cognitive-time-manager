package com.cognitivemanager.config;

import com.cognitivemanager.adapter.in.websocket.CognitiveStateWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket endpoint configuration.
 *
 * Registers the {@link CognitiveStateWebSocketHandler} at the path
 * {@code /ws/cognitive/{developerId}}.
 *
 * Clients connect with: {@code ws://host:8080/ws/cognitive/dev-42}
 *
 * CORS: allows all origins for MVP. Restrict to specific domains in production.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CognitiveStateWebSocketHandler webSocketHandler;

    public WebSocketConfig(CognitiveStateWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(webSocketHandler, "/ws/cognitive/{developerId}")
                .setAllowedOrigins("*"); // Restrict in production
    }
}
