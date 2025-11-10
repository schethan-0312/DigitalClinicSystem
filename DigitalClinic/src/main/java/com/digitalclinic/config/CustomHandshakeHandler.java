package com.digitalclinic.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.Map;

public class CustomHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      org.springframework.web.socket.WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        // Try to use Spring Security Authentication first
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            // Decide representation used for user identity in STOMP:
            // - Use auth.getName() by default (usually username/email)
            // If you prefer to use DB user id, set attribute in HttpSession at login and read it here.
            final String principalName = auth.getName(); // e.g. email or username
            return new Principal() {
                @Override
                public String getName() {
                    return principalName;
                }
            };
        }

        // Fallback to default behavior (anonymous)
        return super.determineUser(request, wsHandler, attributes);
    }
}
