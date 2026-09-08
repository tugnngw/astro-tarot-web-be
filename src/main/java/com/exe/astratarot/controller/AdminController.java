package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.admin.ManagedUserResponse;
import com.exe.astratarot.domain.dto.admin.UpdateUserRoleRequest;
import com.exe.astratarot.domain.dto.admin.UpdateUserStatusRequest;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.reader.ReviewReaderRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ReaderProfileService;
import com.exe.astratarot.service.ReaderService;
import com.exe.astratarot.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ReaderProfileService readerProfileService;
    private final ReaderService readerService;
    private final UserAdminService userAdminService;

    /**
     * Danh sách tài khoản.
     *
     * <p>MANAGER xem được cả bảng, kể cả ADMIN — muốn cất nhắc một người thì
     * phải tìm thấy họ đã. Việc SỬA mới là chỗ bị giới hạn, và mỗi hàng trả về
     * kèm cờ {@code editable} để giao diện biết nút nào được bật.
     */
    @GetMapping("/users")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','STAFF_VIEW')")
    public ResponseEntity<ApiResponse<Page<ManagedUserResponse>>> listUsers(
            @AuthenticationPrincipal CustomUserDetails actor,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Sort theo tên cột thật: truy vấn là native nên Pageable không dịch
        // được tên thuộc tính Java sang cột.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "created_at"));
        return ResponseEntity.ok(ApiResponse.success(userAdminService.list(
                actor.getUser().getId(), role, status, keyword, pageable)));
    }

    @PatchMapping("/users/{userId}/role")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','STAFF_MANAGE')")
    public ResponseEntity<ApiResponse<ManagedUserResponse>> changeUserRole(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã đổi vai trò",
                userAdminService.changeRole(actor.getUser().getId(), userId, request.getRole())));
    }

    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','STAFF_MANAGE')")
    public ResponseEntity<ApiResponse<ManagedUserResponse>> changeUserStatus(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã đổi trạng thái",
                userAdminService.changeStatus(actor.getUser().getId(), userId, request.getStatus())));
    }

    @GetMapping("/readers/applications")
    @PreAuthorize("hasAuthority('ADMIN_READERS_VIEW')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllPendingApplications(
            @AuthenticationPrincipal CustomUserDetails adminDetails) {
        UUID adminId = adminDetails.getUser().getId();
        return ResponseEntity.ok(readerProfileService.getAllPendingApplications(adminId));
    }

    @PatchMapping("/readers/{applicationId}/review")
    @PreAuthorize("hasAuthority('ADMIN_READERS_REVIEW')")
    public ResponseEntity<ApiResponse<Void>> reviewApplication(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable UUID applicationId,
            @RequestBody ReviewReaderRequest request) {
        UUID adminId = adminDetails.getUser().getId();
        readerService.review(adminId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success("Application reviewed", null));
    }
}