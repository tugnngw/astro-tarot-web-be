package com.exe.astratarot.domain.dto.admin;

import com.exe.astratarot.domain.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {
    @NotNull(message = "Phải chọn trạng thái")
    private UserStatus status;
}
