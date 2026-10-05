package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.UserPlanPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPlanPurchaseRepository extends JpaRepository<UserPlanPurchase, UUID> {

    List<UserPlanPurchase> findByUserIdAndStatus(UUID userId, UserPlanPurchase.PurchaseStatus status);

    List<UserPlanPurchase> findByUserIdAndIdNotAndStatus(UUID userId, UUID id, UserPlanPurchase.PurchaseStatus status);

    List<UserPlanPurchase> findByUserId(UUID userId);

    Optional<UserPlanPurchase> findTopByUserIdAndStatusOrderByStartAtDesc(UUID userId, UserPlanPurchase.PurchaseStatus status);

    // =====================================================================
    // Số liệu cho màn Tổng quan của quản trị
    // =====================================================================
    //
    // Mọi truy vấn dưới đây đều lọc priceSnapshot > 0. Gói Free cũng sinh ra
    // một hàng user_plan_purchase; đếm nó vào "số gói đã bán" là thổi phồng
    // con số bằng những lượt chẳng ai trả đồng nào.

    /** Số lượt mua gói có thu tiền. */
    @Query("SELECT COUNT(up) FROM UserPlanPurchase up WHERE up.priceSnapshot > 0")
    long demLuotMuaCoThuTien();

    @Query("SELECT COUNT(up) FROM UserPlanPurchase up "
            + "WHERE up.priceSnapshot > 0 AND up.createdAt >= :tu")
    long demLuotMuaTu(@Param("tu") Timestamp tu);

    /** Tổng tiền thu từ bán gói. Lấy giá ĐÃ CHỤP lúc mua, không lấy giá hiện
     *  tại của gói — đổi bảng giá hôm nay không được phép viết lại doanh thu
     *  của tháng trước. */
    @Query("SELECT COALESCE(SUM(up.priceSnapshot), 0) FROM UserPlanPurchase up "
            + "WHERE up.priceSnapshot > 0")
    long tongTienBanGoi();

    @Query("SELECT COALESCE(SUM(up.priceSnapshot), 0) FROM UserPlanPurchase up "
            + "WHERE up.priceSnapshot > 0 AND up.createdAt >= :tu")
    long tienBanGoiTu(@Param("tu") Timestamp tu);

    /** Số gói đang còn hiệu lực ngay lúc này. */
    @Query("SELECT COUNT(up) FROM UserPlanPurchase up "
            + "WHERE up.status = com.exe.astratarot.domain.entity.UserPlanPurchase$PurchaseStatus.ACTIVE "
            + "AND up.priceSnapshot > 0 AND up.endAt > :bayGio")
    long demGoiConHan(@Param("bayGio") Timestamp bayGio);

    /** Số lượt mua theo từng tên gói, để biết gói nào bán chạy. */
    @Query("SELECT up.planNameSnapshot, COUNT(up) FROM UserPlanPurchase up "
            + "WHERE up.priceSnapshot > 0 GROUP BY up.planNameSnapshot")
    List<Object[]> demTheoTenGoi();

    @Query("SELECT up FROM UserPlanPurchase up WHERE up.userId = :userId AND up.status = :status AND up.startAt <= :startAt AND up.endAt >= :endAt")
    List<UserPlanPurchase> findActivePurchasesInPeriod(
            @Param("userId") UUID userId,
            @Param("status") UserPlanPurchase.PurchaseStatus status,
            @Param("startAt") Timestamp startAt,
            @Param("endAt") Timestamp endAt
    );
}
