package com.exe.astratarot.domain.dto.payout;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePayoutRequest {

    @NotNull(message = "Phải nhập số tiền")
    @Min(value = 1, message = "Số tiền phải lớn hơn 0")
    private Long amount;

    @NotBlank(message = "Phải nhập tên ngân hàng")
    @Size(max = 255)
    private String bankName;

    /**
     * Mã BIN ngân hàng theo chuẩn VietQR, 6 chữ số (970436 = Vietcombank).
     *
     * <p>Không bắt buộc để những lệnh rút tạo bằng phiên bản giao diện cũ vẫn
     * gửi được, nhưng thiếu nó thì không dựng được mã QR và người duyệt phải gõ
     * tay số tài khoản — đúng chỗ dễ gõ nhầm nhất trong cả quy trình.
     */
    @Pattern(regexp = "^$|^[0-9]{6}$", message = "Mã ngân hàng phải là 6 chữ số")
    private String bankBin;

    @NotBlank(message = "Phải nhập số tài khoản")
    @Size(max = 255)
    private String bankAccount;

    @NotBlank(message = "Phải nhập tên chủ tài khoản")
    @Size(max = 255)
    private String accountHolder;
}
