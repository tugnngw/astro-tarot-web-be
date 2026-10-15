package com.exe.astratarot.service;

import java.util.UUID;

/** Đổi một lượt mua trên Google Play lấy gói AI. */
public interface GooglePlayBillingService {

    /** Máy chủ đã có khoá tài khoản dịch vụ chưa. */
    boolean daCauHinh();

    /**
     * Xác minh lượt mua với Google rồi cấp gói.
     *
     * <p>Gọi lại nhiều lần với cùng một {@code purchaseToken} cho ra cùng
     * một kết quả và KHÔNG cấp thêm gói — app phát lại những lượt mua chưa
     * được xác nhận mỗi lần mở lại, nên chuyện gọi lại là bình thường.
     *
     * @return id bản ghi gói đã cấp
     */
    UUID doiLayGoi(UUID userId, String productId, String purchaseToken);
}
