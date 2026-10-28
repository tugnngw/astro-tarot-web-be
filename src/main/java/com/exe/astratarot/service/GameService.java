package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.game.GameDtos.NuocDiResponse;
import com.exe.astratarot.domain.dto.game.GameDtos.TrangThaiResponse;
import com.exe.astratarot.game.NhanVat;

import java.util.UUID;

/** Mini game "Giao Hội": xếp ba biểu tượng, điểm đổi ra lượt AI thưởng. */
public interface GameService {

    /** Luật, hạn mức hôm nay, và phiên đang mở nếu có. */
    TrangThaiResponse trangThai(UUID userId);

    /** Mở phiên mới. Phiên cũ còn mở sẽ được chốt và thưởng trước. */
    TrangThaiResponse batDau(UUID userId, NhanVat nhanVat);

    /** Đổi chỗ hai ô kề nhau. Nước không tạo được chuỗi thì không tốn lượt. */
    NuocDiResponse doiCho(UUID userId, UUID phienId, int o1, int o2);

    /** Dùng kĩ năng nhân vật, nhắm vào một ô. Không tốn lượt đổi chỗ. */
    NuocDiResponse dungKiNang(UUID userId, UUID phienId, int o);
}
