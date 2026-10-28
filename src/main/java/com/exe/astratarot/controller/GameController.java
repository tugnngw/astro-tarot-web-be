package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.game.GameDtos.BatDauRequest;
import com.exe.astratarot.domain.dto.game.GameDtos.DoiChoRequest;
import com.exe.astratarot.domain.dto.game.GameDtos.KiNangRequest;
import com.exe.astratarot.domain.dto.game.GameDtos.NuocDiResponse;
import com.exe.astratarot.domain.dto.game.GameDtos.TrangThaiResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Mini game "Giao Hội": xếp ba biểu tượng, điểm đổi ra lượt AI thưởng.
 *
 * <h2>Vì sao không có trường điểm nào ở phía gửi lên</h2>
 *
 * Điểm đổi được ra lượt AI, mà một lượt AI là một lượt gọi Gemini có hoá đơn
 * thật. Mọi thân yêu cầu ở đây chỉ mang Ô NÀO — máy chủ giữ bàn và tự tính
 * điểm. Không có đường nào để trình duyệt gửi điểm lên.
 *
 * <h2>Vì sao không có {@code @PreAuthorize}</h2>
 *
 * {@code SecurityConfig} kết thúc bằng {@code .anyRequest().authenticated()},
 * nên nhóm đường dẫn này đã đòi đăng nhập. Không gắn quyền riêng là cố ý: chơi
 * game không phải việc của một vai nào, và lượt thưởng rơi vào hạn mức AI của
 * chính người chơi — ai cũng có hạn mức ấy.
 */
@RestController
@RequestMapping("/api/v1/game")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    /** Luật, hạn mức hôm nay, danh sách nhân vật, và phiên đang mở nếu có. */
    @GetMapping("/trang-thai")
    public ResponseEntity<ApiResponse<TrangThaiResponse>> trangThai(
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(ApiResponse.success(
                gameService.trangThai(me.getUser().getId())));
    }

    /** Mở phiên mới. Phiên cũ còn mở được chốt và thưởng trước. */
    @PostMapping("/phien")
    public ResponseEntity<ApiResponse<TrangThaiResponse>> batDau(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody BatDauRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                gameService.batDau(me.getUser().getId(), req.nhanVat())));
    }

    /**
     * Đổi chỗ hai ô kề nhau.
     *
     * <p>Nước không tạo được chuỗi nào bị trả 400 và KHÔNG tốn lượt — bấm nhầm
     * hai ô không được phép ăn một lượt trong hai mươi.
     */
    @PostMapping("/phien/{phienId}/doi-cho")
    public ResponseEntity<ApiResponse<NuocDiResponse>> doiCho(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID phienId,
            @Valid @RequestBody DoiChoRequest req) {
        return ResponseEntity.ok(ApiResponse.success(gameService.doiCho(
                me.getUser().getId(), phienId, req.o1(), req.o2())));
    }

    /** Dùng kĩ năng nhân vật, nhắm vào một ô. Không tốn lượt đổi chỗ. */
    @PostMapping("/phien/{phienId}/ki-nang")
    public ResponseEntity<ApiResponse<NuocDiResponse>> kiNang(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID phienId,
            @Valid @RequestBody KiNangRequest req) {
        return ResponseEntity.ok(ApiResponse.success(gameService.dungKiNang(
                me.getUser().getId(), phienId, req.o())));
    }
}
