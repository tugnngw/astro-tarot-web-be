package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.ai.BaoCaoNoiDungAiRequest;
import com.exe.astratarot.domain.entity.User;

/**
 * Báo cáo nội dung do AI sinh ra.
 *
 * <p>Chính sách AI tạo sinh của CH Play buộc ứng dụng có nội dung do AI sinh
 * phải cho người dùng báo cáo nội dung không phù hợp ngay trong ứng dụng.
 */
public interface BaoCaoNoiDungAiService {

    /** Ghi nhận một báo cáo. Báo lại cùng một lượt trải bài là không làm gì. */
    void bao(User nguoiBao, BaoCaoNoiDungAiRequest yeuCau);

    /** Số báo cáo còn chờ xử lý — cho màn Tổng quan của quản trị. */
    long demChoXuLy();
}
