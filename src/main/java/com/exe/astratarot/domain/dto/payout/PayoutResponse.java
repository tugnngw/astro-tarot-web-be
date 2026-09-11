package com.exe.astratarot.domain.dto.payout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutResponse {
    private UUID id;
    private String readerName;
    private String readerEmail;
    private Long amount;
    private String bankName;
    /** Chỉ bốn số cuối. Màn quản trị hay mở trên máy dùng chung. */
    private String bankAccountMasked;
    private String accountHolder;

    /** Mã BIN ngân hàng theo chuẩn VietQR. Rỗng với các lệnh rút tạo trước đây. */
    private String bankBin;

    /**
     * Chuỗi EMV để vẽ mã QR chuyển khoản.
     *
     * <p>CHỈ trả cho người có quyền duyệt chi, và chỉ khi lệnh còn chờ xử lý:
     * đây là thứ mà quét một cái là chuyển tiền, không phải dữ liệu để bày ra
     * khắp nơi. Reader xem lịch sử của chính mình cũng không cần tới nó.
     */
    private String qrPayload;
    private String status;
    private String rejectReason;
    private Instant requestedAt;
    private Instant processedAt;
}
