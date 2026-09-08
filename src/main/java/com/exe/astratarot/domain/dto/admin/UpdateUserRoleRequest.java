package com.exe.astratarot.domain.dto.admin;

import com.exe.astratarot.domain.enums.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRoleRequest {
    @NotNull(message = "Phải chọn vai trò")
    private UserRole role;
}
