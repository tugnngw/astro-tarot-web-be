package com.exe.astratarot.security;

import com.exe.astratarot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    /**
     * `principal` tới đây có thể là ba dạng khác nhau, xử lý theo thứ tự:
     *   1. UUID  — JwtAuthenticationFilter đặt subject của token là id người dùng.
     *   2. Email — dạng người dùng nhập khi đăng nhập (từ khi đổi sang email).
     *   3. Username — giữ lại cho các tài khoản OAuth và dữ liệu cũ.
     */
    @Override
    public UserDetails loadUserByUsername(String principal) throws UsernameNotFoundException {
        try {
            UUID userId = UUID.fromString(principal);
            return userRepository.findById(userId)
                    .filter(user -> user.getDeletedAt() == null)
                    .map(CustomUserDetails::new)
                    .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng"));
        } catch (IllegalArgumentException ignored) {
            // không phải UUID -> thử email rồi mới tới username
        }

        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(principal)
                .or(() -> userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(principal))
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng"));
    }
}
