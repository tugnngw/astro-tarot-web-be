-- ============================================================
-- Báo cáo nội dung do AI sinh ra.
-- ============================================================
--
-- Chính sách AI tạo sinh của CH Play buộc ứng dụng có nội dung do AI sinh
-- phải cho người dùng BÁO CÁO nội dung không phù hợp ngay trong ứng dụng.
-- Lời giải Tarot do Gemini sinh thuộc diện này, và app chưa có đường nào.
--
-- ------------------------------------------------------------
-- Vì sao không dùng lại bảng reports
-- ------------------------------------------------------------
-- Bảng reports có sẵn, nhưng nó để tố cáo NGƯỜI: cột reported_user_id khai
-- NOT NULL. Nội dung AI thì không có người nào để tố. Nhét một user bất kỳ
-- vào cho hợp lệ sẽ sinh ra những bản ghi tố cáo oan người không liên quan,
-- và hàng chờ xử lý vi phạm sẽ trộn hai loại việc rất khác nhau: kỷ luật một
-- tài khoản, với sửa một lời nhắc của mô hình.
--
-- Nên tách bảng riêng.

CREATE TABLE ai_content_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Ai báo. Giữ khoá ngoại để biết có phải một người báo hàng loạt không.
    -- ON DELETE SET NULL chứ không CASCADE: người báo xoá tài khoản thì báo
    -- cáo vẫn còn giá trị với người xử lý.
    reporter_user_id UUID REFERENCES users(id) ON DELETE SET NULL,

    -- Trỏ tới lượt trải bài bị báo. Để NULL được, vì về sau còn nội dung AI ở
    -- chỗ khác (trò chuyện) cũng dùng chung bảng này.
    reading_id UUID REFERENCES tarot_readings(id) ON DELETE CASCADE,

    -- Chép lại ĐÚNG đoạn bị báo, không chỉ trỏ khoá ngoại.
    --
    -- Lời giải có thể bị sinh lại hoặc xoá đi trước khi có người xem báo cáo,
    -- và lúc ấy báo cáo trở thành vô dụng: biết có người kêu mà không biết kêu
    -- về cái gì.
    noi_dung_bi_bao TEXT,

    ly_do VARCHAR(40) NOT NULL
        CHECK (ly_do IN ('SAI_LECH', 'XUC_PHAM', 'NGUY_HIEM', 'KHAC')),
    mo_ta TEXT,

    trang_thai VARCHAR(20) NOT NULL DEFAULT 'CHO_XU_LY'
        CHECK (trang_thai IN ('CHO_XU_LY', 'DA_XEM', 'BO_QUA')),
    nguoi_xu_ly_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ghi_chu_xu_ly TEXT,
    xu_ly_luc TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Màn quản trị đếm số báo cáo chờ xử lý mỗi lần mở, nên lọc theo trạng thái
-- phải nhanh.
CREATE INDEX idx_ai_reports_trang_thai ON ai_content_reports(trang_thai);
CREATE INDEX idx_ai_reports_reading ON ai_content_reports(reading_id);

-- Một người chỉ báo một lượt trải bài một lần. Bấm nhầm hai lần không được
-- phép thành hai việc cho người xử lý.
CREATE UNIQUE INDEX idx_ai_reports_mot_lan
    ON ai_content_reports(reporter_user_id, reading_id)
    WHERE reporter_user_id IS NOT NULL AND reading_id IS NOT NULL;
