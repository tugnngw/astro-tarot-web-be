package com.exe.astratarot.domain.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Hồ sơ đầy đủ của một tài khoản nhìn từ màn quản trị, kèm số liệu hoạt động. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedUserDetailResponse {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String avatar;
    private String gender;
    private LocalDate dateOfBirth;
    private String bio;
    private String address;
    private String city;
    private String country;
    private String role;
    private String status;
    private Boolean emailVerified;
    private String authProvider;
    private Instant lastLoginAt;
    private Instant createdAt;

    /** Quyền suy ra từ vai trò — để người sắp đổi vai trò thấy mình đang trao gì. */
    private List<String> permissions;
    private Boolean editable;

    // ----- Số liệu hoạt động -----
    /** Số phiên đăng nhập còn hiệu lực. Bằng 0 nghĩa là đang đăng xuất hết. */
    private long activeSessions;
    private long orderCount;
    private long totalSpent;
    /** Có hồ sơ Reader hay không — quyết định người này nhận booking được không. */
    private Boolean hasReaderProfile;
    private Boolean hasPendingReaderApplication;
}
