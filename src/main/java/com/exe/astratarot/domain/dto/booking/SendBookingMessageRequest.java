package com.exe.astratarot.domain.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendBookingMessageRequest {

    /**
     * Giới hạn 4000 ký tự. Không phải con số tuỳ tiện: cột là TEXT nên
     * Postgres không chặn, mà không chặn thì một client hỏng có thể nhét vài
     * megabyte vào một tin rồi đẩy thẳng qua WebSocket tới máy người kia.
     */
    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    @Size(max = 4000, message = "Tin nhắn tối đa 4000 ký tự")
    private String body;
}
