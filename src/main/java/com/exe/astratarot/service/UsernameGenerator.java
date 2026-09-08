package com.exe.astratarot.service;

import com.exe.astratarot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Sinh username từ email.
 *
 * <p>Cột username là NOT NULL UNIQUE từ V1_1 và luồng OAuth vẫn đang dùng, nên
 * không bỏ được; nhưng người dùng không cần biết tới nó nữa. Nối thêm 8 ký tự
 * ngẫu nhiên để hai người có email khác nhau mà trùng phần đầu (ví dụ
 * an@a.com và an@b.com) không đụng nhau.
 *
 * <p>Tách thành component riêng vì cả luồng tự đăng ký lẫn luồng quản trị viên
 * tạo tài khoản đều cần — chép làm hai bản là sớm muộn hai bên lệch luật.
 */
@Component
@RequiredArgsConstructor
public class UsernameGenerator {

    private final UserRepository userRepository;

    public String fromEmail(String email) {
        String base = email.split("@")[0]
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "");
        if (base.isEmpty()) {
            base = "user";
        }
        base = base.substring(0, Math.min(base.length(), 40));

        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = base + "_" + UUID.randomUUID().toString().substring(0, 8);
            if (!userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không sinh được username duy nhất");
    }
}
