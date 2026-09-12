package com.exe.astratarot.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * Xác thực STOMP CONNECT bằng JWT trong header {@code Authorization}.
 *
 * <p>{@code Principal.getName()} = user UUID — khớp
 * {@code convertAndSendToUser(userId.toString(), ...)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractBearer(accessor);
            if (token == null) {
                throw new IllegalArgumentException("Thiếu Authorization trên STOMP CONNECT");
            }
            String subject = jwtService.extractSubjectSafely(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(subject);
            if (!jwtService.validateToken(token, userDetails)) {
                throw new IllegalArgumentException("JWT không hợp lệ trên STOMP CONNECT");
            }
            // Name = UUID để /user/queue/... resolve đúng người nhận.
            Principal principal = new UsernamePasswordAuthenticationToken(
                    subject, null, userDetails.getAuthorities());
            accessor.setUser(principal);
            log.debug("STOMP CONNECT ok cho {}", subject);
        }

        return message;
    }

    private static String extractBearer(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader("Authorization");
        if (values == null || values.isEmpty()) {
            return null;
        }
        String raw = values.getFirst();
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }
}
