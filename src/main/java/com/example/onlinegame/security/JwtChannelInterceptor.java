package com.example.onlinegame.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            log.info("[WS] CONNECT attempt. Authorization header: {}", authHeader);

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                log.info("[WS] Extracted JWT: {}", token);

                if (jwtUtil.validateToken(token)) {
                    UserPrincipal userPrincipal = jwtUtil.getUserPrincipal(token);
                    if (userPrincipal != null) {
                        Authentication auth = new UsernamePasswordAuthenticationToken(userPrincipal, null, List.of());
                        Object principal = auth.getPrincipal();
                        if (principal instanceof UserPrincipal) {
                            accessor.setUser(userPrincipal);
                        } else {
                            log.info("[WS] Error cast Principal to UserPrincipal: {}", userPrincipal.getUsername());
                        }
                        log.info("[WS] JWT is valid. User authenticated as: {}", userPrincipal.getUsername());
                    } else {
                        log.warn("[WS] Token valid, but userPrincipal is null");
                    }
                } else {
                    log.warn("[WS] Invalid JWT token");
                }
            } else {
                log.warn("[WS] No Bearer token in Authorization header");
            }
        }

        return message;
    }
}