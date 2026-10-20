-- ============================================================
-- Khuyến mãi theo dịp lễ, kèm lời chúc gửi đúng MỘT lần cho mỗi người.
--
-- Hai bảng vì hai việc khác nhau:
--   khuyen_mai         — đợt khuyến mãi, do quản trị nhập
--   khuyen_mai_da_chuc — ai đã xem lời chúc của đợt nào
--
-- Vì sao "đã chúc" phải là một bảng riêng, không phải một cột trên users:
-- mỗi người có thể xem lời chúc của NHIỀU đợt (Tết, Trung thu, 30/4), và
-- số đợt tăng theo thời gian. Nhét vào một cột thì hoặc chỉ nhớ được đợt
-- cuối, hoặc phải cất một mảng rồi tự quản lý.
-- ============================================================

CREATE TABLE IF NOT EXISTS khuyen_mai (
    id              UUID PRIMARY KEY,
    ten             VARCHAR(120)  NOT NULL,

    -- Lời chúc hiện một lần khi người dùng đăng nhập. Để trống thì đợt này
    -- chỉ giảm giá, không chúc gì cả — nhiều đợt không cần lời chúc.
    loi_chuc        TEXT,

    -- PHAN_TRAM: gia_tri là % (1..100). SO_TIEN: gia_tri là số đồng.
    loai_giam       VARCHAR(20)   NOT NULL,
    gia_tri         BIGINT        NOT NULL,

    -- Trần giảm cho loại PHAN_TRAM. NULL là không chặn.
    --
    -- Có cột này vì "giảm 50%" trên gói 239.000 là 119.500 — một con số
    -- quản trị không nhìn thấy lúc gõ "50". Trần giá chặn đúng loại sai sót
    -- ấy, và sai sót ấy mất tiền thật.
    giam_toi_da     BIGINT,

    bat_dau         TIMESTAMPTZ   NOT NULL,
    ket_thuc        TIMESTAMPTZ   NOT NULL,

    -- Tắt tay mà không phải sửa ngày. Quản trị cần tắt gấp một đợt đang
    -- chạy thì sửa ngày kết thúc là làm hỏng cả lịch sử của đợt đó.
    dang_bat        BOOLEAN       NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,

    CONSTRAINT ck_khuyen_mai_loai  CHECK (loai_giam IN ('PHAN_TRAM', 'SO_TIEN')),
    CONSTRAINT ck_khuyen_mai_gia   CHECK (gia_tri > 0),
    -- Phần trăm phải nằm trong 1..100. Gõ nhầm "150" thì khách được trả
    -- tiền để mua gói.
    CONSTRAINT ck_khuyen_mai_pt    CHECK (loai_giam <> 'PHAN_TRAM' OR gia_tri BETWEEN 1 AND 100),
    CONSTRAINT ck_khuyen_mai_ngay  CHECK (ket_thuc > bat_dau)
);

-- Truy vấn nóng nhất: "đợt nào đang chạy ngay bây giờ". Gọi ở mỗi lần mở
-- trang gói AI và mỗi lần đăng nhập.
CREATE INDEX IF NOT EXISTS idx_khuyen_mai_dang_chay
    ON khuyen_mai (dang_bat, bat_dau, ket_thuc);

CREATE TABLE IF NOT EXISTS khuyen_mai_da_chuc (
    khuyen_mai_id   UUID        NOT NULL REFERENCES khuyen_mai (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    xem_luc         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Khoá chính ghép CHÍNH LÀ thứ bảo đảm "chỉ một lần".
    --
    -- Không dựa vào việc ứng dụng kiểm trước rồi mới ghi: người dùng mở app
    -- trên điện thoại và web gần như cùng lúc thì cả hai đều đọc thấy "chưa
    -- xem", và cả hai cùng hiện lời chúc. Để CSDL từ chối hàng thứ hai thì
    -- không có khe nào lọt.
    PRIMARY KEY (khuyen_mai_id, user_id)
);
