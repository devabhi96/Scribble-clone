package com.scribble.backend.config;

import com.scribble.backend.security.JwtService;
import com.scribble.backend.security.JwtService.VerifiedIdentity;
import com.scribble.backend.websocket.StompPrincipal;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class StompHandshakeInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public StompHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new MessagingException("Missing bearer token on CONNECT");
            }

            try {
                VerifiedIdentity identity = jwtService.verify(authHeader.substring(7));
                accessor.setUser(new StompPrincipal(identity.subject()));
            } catch (JwtException e) {
                throw new MessagingException("Invalid or expired token on CONNECT", e);
            }
        }
        return message;
    }
}