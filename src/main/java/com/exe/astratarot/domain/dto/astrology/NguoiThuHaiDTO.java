package com.exe.astratarot.domain.dto.astrology;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Người thứ hai trong một lượt xem đôi (tương hợp, chuyện tình cảm).
 *
 * Người thứ nhất luôn là chủ tài khoản: dữ liệu của họ lấy từ hồ sơ chiêm tinh
 * đã lưu, theo userId trong JWT. Người thứ hai thì KHÔNG có hồ sơ — họ không
 * phải người dùng của hệ thống — nên dữ liệu đi kèm từng yêu cầu một.
 *
 * Gửi kèm từng yêu cầu chứ không lưu vào hồ sơ là có chủ đích: lưu lại thì
 * người bạn đời của phiên tuần trước sẽ lẳng lặng chui vào lượt xem một mình
 * của hôm nay, và không ai nhìn thấy để mà gỡ.
 *
 * Mọi trường đều không bắt buộc. Thiếu ngày sinh thì vẫn còn cái tên, và chỉ
 * riêng nó đã đủ để lời giải nói tới hai người thay vì một.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NguoiThuHaiDTO {

    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    private LocalDate birthDate;

    private LocalTime birthTime;

    @Size(max = 255, message = "Birth place cannot exceed 255 characters")
    private String birthPlace;

    @Size(max = 50, message = "Timezone cannot exceed 50 characters")
    private String timezone;

    /** Rỗng hoàn toàn thì coi như không có người thứ hai. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isRong() {
        return (name == null || name.isBlank())
                && birthDate == null
                && (birthPlace == null || birthPlace.isBlank());
    }

    /**
     * Tên đã gọt, hoặc null nếu không có.
     *
     * Tĩnh và nhận null để chỗ gọi khỏi phải tự kiểm hai lần — trước đây mỗi
     * service tự kiểm lấy thì chỉ cần một bên quên `isBlank()` là prompt in
     * ra "NGƯỜI THỨ HAI — " cụt đuôi.
     */
    public static String tenCua(NguoiThuHaiDTO nguoi) {
        if (nguoi == null || nguoi.getName() == null || nguoi.getName().isBlank()) {
            return null;
        }
        return nguoi.getName().trim();
    }

    /** Có đủ dữ liệu để tính cung hay chưa. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean coNgaySinh() {
        return birthDate != null;
    }
}
