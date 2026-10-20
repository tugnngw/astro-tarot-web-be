package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.khuyenmai.KhuyenMaiResponse;
import com.exe.astratarot.domain.dto.khuyenmai.LuuKhuyenMaiRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.KhuyenMaiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Khuyến mãi theo dịp lễ, và lời chúc gửi một lần. */
@RestController
@RequestMapping("/api/v1/khuyen-mai")
@RequiredArgsConstructor
public class KhuyenMaiController {

    private final KhuyenMaiService khuyenMaiService;

    /** Đợt đang chạy — khách chưa đăng nhập cũng xem được banner. */
    @GetMapping("/dang-chay")
    public ResponseEntity<ApiResponse<List<KhuyenMaiResponse>>> dangChay() {
        return ResponseEntity.ok(ApiResponse.success(khuyenMaiService.dangChay()));
    }

    /** Giá sau giảm của mọi gói đang bán — giao diện chỉ việc hiện. */
    @GetMapping("/gia-goi")
    public ResponseEntity<ApiResponse<java.util.Map<String,
            com.exe.astratarot.domain.dto.khuyenmai.GiaSauGiam>>> giaGoi() {
        return ResponseEntity.ok(ApiResponse.success(khuyenMaiService.giaMoiGoi()));
    }

    /**
     * Lời chúc chưa xem của người đang đăng nhập.
     *
     * <p>Là POST chứ không phải GET vì nó ĐỔI trạng thái: đánh dấu đã xem
     * ngay trong cùng lượt gọi. Để GET thì trình duyệt hay proxy nào đó gọi
     * lại một phát là lời chúc biến mất trước khi người dùng kịp thấy.
     *
     * <p>Trả 204 khi không có gì để chúc.
     */
    @PostMapping("/loi-chuc")
    public ResponseEntity<ApiResponse<KhuyenMaiResponse>> loiChuc(
            @AuthenticationPrincipal CustomUserDetails me) {
        if (me == null) return ResponseEntity.noContent().build();
        return khuyenMaiService.loiChucChuaXem(me.getUser().getId())
                .map(k -> ResponseEntity.ok(ApiResponse.success(k)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // ---- Quản trị ----

    @GetMapping("/admin")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<List<KhuyenMaiResponse>>> tatCa() {
        return ResponseEntity.ok(ApiResponse.success(khuyenMaiService.tatCa()));
    }

    @PostMapping("/admin")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<KhuyenMaiResponse>> tao(
            @Valid @RequestBody LuuKhuyenMaiRequest request) {
        return ResponseEntity.ok(ApiResponse.success(khuyenMaiService.tao(request)));
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<KhuyenMaiResponse>> sua(
            @PathVariable UUID id,
            @Valid @RequestBody LuuKhuyenMaiRequest request) {
        return ResponseEntity.ok(ApiResponse.success(khuyenMaiService.sua(id, request)));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> xoa(@PathVariable UUID id) {
        khuyenMaiService.xoa(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
