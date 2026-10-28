package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.GamePhien;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GamePhienRepository extends JpaRepository<GamePhien, UUID> {

    /**
     * Phiên của một người, tìm theo id.
     *
     * <p>Lọc luôn theo {@code userId} ở câu truy vấn chứ không tìm theo id rồi
     * so chủ sở hữu ở tầng trên: quên mất phép so ấy là ai có id phiên của
     * người khác cũng đi được nước đi trong phiên của họ.
     */
    Optional<GamePhien> findByIdAndUserId(UUID id, UUID userId);

    /** Các phiên chưa chốt của một người, mới nhất trước. */
    @Query("SELECT p FROM GamePhien p WHERE p.userId = :userId AND p.daChot = false "
            + "ORDER BY p.createdAt DESC")
    List<GamePhien> timPhienChuaChot(@Param("userId") UUID userId);

    /**
     * Phiên của một người, khoá lại để ghi.
     *
     * <h2>Vì sao cần khoá</h2>
     *
     * Mỗi nước đi đọc điểm của phiên, cộng thêm, rồi ghi lại. Bắn hai yêu cầu
     * cùng một nước đi song song thì cả hai đọc được cùng một điểm cũ, và điểm
     * của nước đi ấy được cộng hai lần — một người biết mở tab Network là làm
     * được, mà điểm thì đổi ra lượt AI có hoá đơn thật.
     *
     * <p>Khoá hàng phiên lại cho yêu cầu thứ hai phải chờ, nên nó đọc được điểm
     * đã cộng và bàn đã đổi; nước đi của nó khi đó không còn hợp lệ nữa và bị
     * từ chối, đúng như khi người chơi bấm hai lần thật.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM GamePhien p WHERE p.id = :id AND p.userId = :userId")
    Optional<GamePhien> timVaKhoa(@Param("id") UUID id, @Param("userId") UUID userId);
}
