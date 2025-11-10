// config/WebSocketConfig.java
package com.digitalclinic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // ✅ Enables simple in-memory broker for public & private destinations
        config.enableSimpleBroker("/topic", "/queue");

        // ✅ Application destination prefix (for @MessageMapping endpoints)
        config.setApplicationDestinationPrefixes("/app");

        // ✅ Prefix for user-specific (private) queues
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ WebSocket endpoint used by SockJS clients
        // Added CustomHandshakeHandler to correctly assign authenticated Principal to each session
        registry.addEndpoint("/ws-video-consultation")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(new CustomHandshakeHandler()) // <--- Added line
                .withSockJS();

        // You can add more endpoints here if needed (example: /ws-chat, /ws-support, etc.)
    }
}
