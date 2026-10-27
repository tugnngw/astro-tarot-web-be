package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.AuthProvider;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.TaiKhoanGoogleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Nối một tài khoản Google vào người dùng của hệ thống.
 *
 * <h2>Vì sao tách thành dịch vụ riêng</h2>
 *
 * Có HAI lối vào Google: web đi qua {@code OAuth2LoginSuccessHandler}, app đi
 * qua endpoint nhận ID token. Để mỗi lối tự viết phần nối tài khoản là có hai
 * bản cài đặt của cùng một quy tắc, và sai lệch ở đây không báo lỗi — nó sinh
 * ra TÀI KHOẢN TRÙNG. Người dùng mất lịch sử, mất số dư ví, và phải phát hiện
 * ra điều đó bằng cách đăng nhập rồi thấy ví trống.
 */
@Service
@RequiredArgsConstructor
public class TaiKhoanGoogleServiceImpl implements TaiKhoanGoogleService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User timHoacTao(String providerId, String email, boolean emailDaXacMinh,
                           String hoTen, String anhDaiDien) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Thiếu định danh Google của tài khoản.");
        }
        // Google phải xác nhận email đã verify. Thiếu kiểm tra này thì ai đó
        // tạo một tài khoản Google mang email của người khác là chiếm được
        // tài khoản ở đây.
        if (!emailDaXacMinh) {
            throw new IllegalArgumentException("Email Google chưa được xác minh.");
        }

        String emailChuan = chuanHoaEmail(email);
        String ten = chuanHoaHoTen(hoTen, emailChuan);

        return userRepository
                .findByAuthProviderAndProviderIdAndDeletedAtIsNull(AuthProvider.GOOGLE, providerId)
                .map(u -> capNhat(u, emailChuan, ten, anhDaiDien, providerId))
                .orElseGet(() -> noiHoacTao(emailChuan, ten, anhDaiDien, providerId));
    }

    /**
     * Chưa từng đăng nhập Google: nối vào tài khoản sẵn có cùng email, hoặc
     * tạo mới.
     *
     * <p>Nối theo email là chỗ quan trọng nhất: người đăng ký bằng email từ
     * trước, sau này bấm nút Google, phải vào đúng tài khoản cũ với đủ lịch
     * sử và số dư ví — không phải một tài khoản trắng.
     *
     * <h3>Vì sao nối cả tài khoản đăng nhập bằng MẬT KHẨU</h3>
     *
     * Bản trước từ chối, với lý do: "gộp tự động nghĩa là ai đăng ký Google
     * bằng email của người khác cũng vào được tài khoản của họ". Lý do ấy đã
     * bị chính {@link #timHoacTao} vô hiệu từ trước: nó bắt buộc
     * {@code email_verified}, tức Google đã xác nhận người đang đăng nhập làm
     * chủ hòm thư ấy. Không ai tạo được một tài khoản Google mang Gmail của
     * người khác rồi được Google xác nhận.
     *
     * <p>Và nó KHÔNG mở thêm cửa nào: hệ thống vốn đã có đặt lại mật khẩu qua
     * email. Ai đọc được hòm thư ấy thì đã vào được tài khoản rồi. Nối theo
     * email đã xác minh không yếu hơn lối sẵn có.
     *
     * <p>Cái giá của việc từ chối thì đo được: tới 27/10/2026, CẢ 12 tài
     * khoản trên production đều là {@code LOCAL}. Nghĩa là nhánh từ chối này
     * bắt mọi lần bấm nút Google — và vì nó ném từ bên trong
     * {@code onAuthenticationSuccess}, nơi không ai bắt, người dùng nhận
     * HTTP 500 chứ không phải một câu giải thích.
     *
     * <p>Mật khẩu cũ KHÔNG bị xoá, nên ai đã quen đăng nhập bằng mật khẩu thì
     * vẫn đăng nhập được như cũ. {@code authProvider} không chặn lối mật khẩu
     * ở bất cứ đâu — nó chỉ dùng để hiện ra và lúc tạo tài khoản.
     */
    private User noiHoacTao(String email, String hoTen, String anhDaiDien, String providerId) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .map(u -> capNhat(u, email, hoTen, anhDaiDien, providerId))
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(tenDangNhapChuaTrung(email))
                        .email(email)
                        .fullName(hoTen)
                        .avatar(anhDaiDien)
                        .authProvider(AuthProvider.GOOGLE)
                        .providerId(providerId)
                        .emailVerified(true)
                        .emailVerifiedAt(Instant.now())
                        .build()));
    }

    /**
     * Cập nhật thông tin từ Google, nhưng KHÔNG đè lên thứ người dùng đã tự
     * đặt.
     *
     * <p>Ai đó đổi tên hiển thị hoặc ảnh đại diện trong app rồi đăng nhập
     * lại bằng Google — đè lên là xoá công của họ mỗi lần đăng nhập.
     */
    private User capNhat(User u, String email, String hoTen, String anhDaiDien, String providerId) {
        u.setAuthProvider(AuthProvider.GOOGLE);
        u.setProviderId(providerId);
        u.setEmail(email);
        u.setEmailVerified(true);
        if (u.getEmailVerifiedAt() == null) {
            u.setEmailVerifiedAt(Instant.now());
        }
        if (u.getFullName() == null || u.getFullName().isBlank()) {
            u.setFullName(hoTen);
        }
        if (u.getAvatar() == null || u.getAvatar().isBlank()) {
            u.setAvatar(anhDaiDien);
        }
        u.setPendingEmail(null);
        u.setEmailVerificationToken(null);
        u.setEmailVerificationExpiresAt(null);
        return userRepository.save(u);
    }

    private String tenDangNhapChuaTrung(String email) {
        String goc = email.substring(0, email.indexOf('@'))
                .replaceAll("[^a-zA-Z0-9_]", "_")
                .toLowerCase();
        if (goc.isBlank()) {
            goc = "google_user";
        }
        String ten = goc;
        while (userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(ten)) {
            ten = goc + "_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return ten;
    }

    private static String chuanHoaEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Tài khoản Google không có email.");
        }
        return email.trim().toLowerCase();
    }

    private static String chuanHoaHoTen(String hoTen, String email) {
        if (hoTen != null && !hoTen.isBlank()) {
            return hoTen.trim();
        }
        // Không có tên thì lấy CẢ email làm tên tạm. Cắt lấy phần trước @ có
        // vẻ gọn hơn, nhưng đó là đổi hành vi đang chạy: bản cũ dùng cả
        // email, và người dùng hiện có đang mang tên ấy.
        return email;
    }
}
