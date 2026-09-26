package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.AuthProvider;
import com.exe.astratarot.domain.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Ghi mốc "còn thấy lần cuối".
     *
     * <p>Câu UPDATE thẳng chứ không nạp entity rồi sửa: ảnh đại diện nằm
     * ngay trong bảng users (xem V2_6), nên nạp cả User chỉ để đặt một dấu
     * thời gian là kéo theo vài trăm KB nhị phân mỗi lần ai đó mở hay đóng
     * một tab.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.lastSeenAt = :luc WHERE u.id = :id")
    void ghiLastSeen(@Param("id") UUID id, @Param("luc") Instant luc);

    /** Chỉ lấy mốc thời gian, cùng lý do như trên. */
    @Query("SELECT u.lastSeenAt FROM User u WHERE u.id = :id")
    Optional<Instant> lastSeenCua(@Param("id") UUID id);
    Optional<User> findByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<User> findByAuthProviderAndProviderIdAndDeletedAtIsNull(AuthProvider authProvider, String providerId);

    Optional<User> findByEmailVerificationToken(String emailVerificationToken);

    Optional<User> findByPasswordResetToken(String passwordResetToken);

    boolean existsByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    long countByRoleAndDeletedAtIsNull(UserRole role);

    /** Nhân viên / quản lý còn hoạt động — dùng khi ticket chưa ai nhận mà khách reply. */
    List<User> findByRoleInAndDeletedAtIsNull(List<UserRole> roles);

    // Dem tai khoan moi tao sau moc thoi gian (bo qua tai khoan da xoa mem).
    long countByCreatedAtAfterAndDeletedAtIsNull(java.time.Instant since);

    /**
     * Tìm tài khoản cho màn quản lý.
     *
     * <p>Chuỗi rỗng nghĩa là "không lọc", thay vì {@code :param IS NULL}. JPQL
     * gửi null mà không kèm kiểu, Postgres tự suy ra bytea rồi lower(bytea) nổ
     * ngay — đã dính đúng lỗi này ở ProductRepository.search. Mọi tham số ở đây
     * luôn là String khác null nên không rơi vào tình huống đó.
     */
    @Query(value = """
            SELECT * FROM users u
            WHERE u.deleted_at IS NULL
              AND (:role = '' OR u.role = :role)
              AND (:status = '' OR u.status = :status)
              AND (:keyword = ''
                   OR lower(u.email) LIKE lower('%' || :keyword || '%')
                   OR lower(u.full_name) LIKE lower('%' || :keyword || '%')
                   OR lower(u.username) LIKE lower('%' || :keyword || '%'))
            """,
            countQuery = """
            SELECT count(*) FROM users u
            WHERE u.deleted_at IS NULL
              AND (:role = '' OR u.role = :role)
              AND (:status = '' OR u.status = :status)
              AND (:keyword = ''
                   OR lower(u.email) LIKE lower('%' || :keyword || '%')
                   OR lower(u.full_name) LIKE lower('%' || :keyword || '%')
                   OR lower(u.username) LIKE lower('%' || :keyword || '%'))
            """,
            nativeQuery = true)
    Page<User> searchForAdmin(@Param("role") String role,
                              @Param("status") String status,
                              @Param("keyword") String keyword,
                              Pageable pageable);
}
