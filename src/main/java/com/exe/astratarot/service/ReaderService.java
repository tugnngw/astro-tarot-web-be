package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.reader.ApplyReaderRequest;
import com.exe.astratarot.domain.dto.reader.ReaderApplicationResponse;
import com.exe.astratarot.domain.dto.reader.ReviewReaderRequest;

import java.util.Optional;
import java.util.UUID;

public interface ReaderService {

    /** Kết quả của một lần nộp hồ sơ Reader. */
    enum ApplyOutcome {
        /** Người ngoài nộp đơn — vào hàng chờ duyệt. */
        SUBMITTED,
        /** Nhân viên tạo hồ sơ — có ngay, không qua hàng chờ. */
        APPROVED_IMMEDIATELY
    }

    ApplyOutcome apply(UUID userId, ApplyReaderRequest request);

    void review(UUID reviewerId, UUID applicationId, ReviewReaderRequest request);

    /** Đơn gần nhất của chính người đang đăng nhập; rỗng nếu chưa từng nộp. */
    Optional<ReaderApplicationResponse> myApplication(UUID userId);
}
