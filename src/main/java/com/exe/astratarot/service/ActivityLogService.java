package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.admin.ActivityLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

/**
 * Nhật ký thao tác quản trị.
 *
 * <p>Ghi lại AI làm gì, LÊN AI, và thay đổi cụ thể ra sao. Không có nó thì khi
 * một tài khoản bị hạ quyền hay bị khoá, không ai truy được là ai làm và vì sao
 * — mà đó đúng là những thao tác dễ gây tranh cãi nhất.
 */
public interface ActivityLogService {

    /** Ghi một dòng nhật ký. Không bao giờ ném lỗi ra ngoài. */
    void record(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> changes);

    Page<ActivityLogResponse> list(String action, String entityType, Pageable pageable);
}
