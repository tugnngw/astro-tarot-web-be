package com.exe.astratarot.config;

import com.exe.astratarot.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;

/**
 * STOMP over WebSocket — đẩy thông báo realtime tới từng user đã đăng nhập.
 *
 * <p>Client kết nối {@code /ws}, gửi CONNECT kèm {@code Authorization: Bearer},
 * rồi subscribe {@code /user/queue/events}. Auth thật nằm ở CONNECT (xem
 * {@link StompAuthChannelInterceptor}), không dựa cookie session.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    /**
     * Chặn bộ đệm WebSocket, vì đây là bộ nhớ NGOÀI heap.
     *
     * <p>Mặc định mỗi phiên được giữ tới 512KB hàng chờ gửi và không có hạn
     * thời gian. Một máy khách "chết mà chưa đóng" — tắt wifi, gập máy, 4G
     * mất sóng — vẫn được giữ bộ đệm cho tới khi TCP tự hết hạn, có thể vài
     * phút. Vài chục phiên như vậy là đủ đẩy container qua mức 512MB của
     * Render, và nó bị giết mà Java không hề báo OutOfMemoryError.
     *
     * <p>Đặt hạn thời gian gửi là phần quan trọng nhất ở đây: nó biến "giữ bộ
     * đệm vô hạn cho một kết nối đã chết" thành "10 giây rồi cắt".
     *
     * <p>64KB cho một tin là rộng rãi — tin nhắn bị chặn ở 4000 ký tự, còn
     * gói tín hiệu WebRTC lớn nhất (SDP) thường dưới 10KB.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(64 * 1024);
        registration.setSendBufferSizeLimit(256 * 1024);
        registration.setSendTimeLimit(10_000);
    }
}
