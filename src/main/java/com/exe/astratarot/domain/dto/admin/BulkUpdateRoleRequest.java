package com.exe.astratarot.domain.dto.admin;

import com.exe.astratarot.domain.enums.UserRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkUpdateRoleRequest {

    @NotEmpty(message = "Phải chọn ít nhất một tài khoản")
    @Size(max = 100, message = "Mỗi lần tối đa 100 tài khoản")
    private List<UUID> userIds;

    @NotNull(message = "Phải chọn vai trò")
    private UserRole role;
}
