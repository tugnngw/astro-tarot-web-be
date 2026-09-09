package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.admin.ActivityLogResponse;
import com.exe.astratarot.domain.dto.admin.AdminStatsResponse;
import com.exe.astratarot.domain.dto.admin.BulkUpdateRoleRequest;
import com.exe.astratarot.domain.dto.admin.CreateUserRequest;
import com.exe.astratarot.domain.dto.admin.ManagedUserDetailResponse;
import com.exe.astratarot.domain.dto.admin.ManagedUserResponse;
import com.exe.astratarot.domain.dto.admin.UpdateUserInfoRequest;
import com.exe.astratarot.domain.dto.admin.UpdateUserRoleRequest;
import com.exe.astratarot.domain.dto.admin.UpdateUserStatusRequest;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.reader.ReviewReaderRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ReaderProfileService;
import com.exe.astratarot.service.ReaderService;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.AdminStatsService;
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
    private final ActivityLogService activityLogService;
    private final AdminStatsService adminStatsService;

    /**
     * Số liệu tổng quan cho trang Quản trị.
     *
     * Chỉ đọc và toàn số đếm, không kèm dữ liệu cá nhân. Cùng quyền với các
     * thao tác quản trị khác (USERS_MANAGE hoặc AUDIT_VIEW), đều là quyền ADMIN.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.success(adminStatsService.getStats()));
    }

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

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','STAFF_VIEW')")
    public ResponseEntity<ApiResponse<ManagedUserDetailResponse>> userDetail(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                userAdminService.detail(actor.getUser().getId(), userId)));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','STAFF_MANAGE')")
    public ResponseEntity<ApiResponse<ManagedUserResponse>> createUser(
            @AuthenticationPrincipal CustomUserDetails actor,
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạo tài khoản",
                userAdminService.create(actor.getUser().getId(), request)));
    }

    @PatchMapping("/users/{userId}")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','STAFF_MANAGE')")
    public ResponseEntity<ApiResponse<ManagedUserResponse>> updateUserInfo(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserInfoRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật thông tin",
                userAdminService.updateInfo(actor.getUser().getId(), userId, request)));
    }

    /**
     * Đổi vai trò hàng loạt. Cả lô nằm trong một transaction: sai một tài khoản
     * là rollback toàn bộ, vì một lô nửa thành công không ai kiểm lại được.
     */
    @PatchMapping("/users/role")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','STAFF_MANAGE')")
    public ResponseEntity<ApiResponse<List<ManagedUserResponse>>> changeRoleBulk(
            @AuthenticationPrincipal CustomUserDetails actor,
            @Valid @RequestBody BulkUpdateRoleRequest request) {
        List<ManagedUserResponse> updated = userAdminService.changeRoleBulk(
                actor.getUser().getId(), request.getUserIds(), request.getRole());
        return ResponseEntity.ok(ApiResponse.success(
                "Đã đổi vai trò cho " + updated.size() + " tài khoản", updated));
    }

    @PostMapping("/users/{userId}/sessions/revoke")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','STAFF_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> revokeSessions(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID userId) {
        userAdminService.revokeSessions(actor.getUser().getId(), userId);
        return ResponseEntity.ok(ApiResponse.success("Đã buộc đăng xuất khỏi mọi thiết bị", null));
    }

    @PostMapping("/users/{userId}/password-reset")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','STAFF_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> sendPasswordReset(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID userId) {
        userAdminService.sendPasswordReset(actor.getUser().getId(), userId);
        return ResponseEntity.ok(ApiResponse.success("Đã gửi liên kết đặt lại mật khẩu", null));
    }

    @PostMapping("/users/{userId}/resend-verification")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','STAFF_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID userId) {
        userAdminService.resendVerification(actor.getUser().getId(), userId);
        return ResponseEntity.ok(ApiResponse.success("Đã gửi lại mail xác minh", null));
    }

    /** Xoá mềm. Chỉ ADMIN — kiểm tra thêm một lần nữa ở tầng service. */
    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable UUID userId) {
        userAdminService.softDelete(actor.getUser().getId(), userId);
        return ResponseEntity.ok(ApiResponse.success("Đã xoá tài khoản", null));
    }

    // ---------- Nhật ký hệ thống ----------

    @GetMapping("/activity-logs")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<Page<ActivityLogResponse>>> activityLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.success(activityLogService.list(
                action, entityType, PageRequest.of(page, size))));
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