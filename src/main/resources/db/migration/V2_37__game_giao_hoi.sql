-- Mini game "Giao Hội": xếp ba biểu tượng, điểm đổi ra lượt AI thưởng.
--
-- Vì sao bàn chơi nằm trong cơ sở dữ liệu
--
-- Điểm đổi được ra lượt AI, mà một lượt AI là một lượt gọi Gemini có hoá đơn
-- thật. Nên máy chủ phải là nơi duy nhất tính điểm, và muốn vậy thì bàn chơi
-- phải ở phía máy chủ. Mỗi nước đi là một lượt gọi HTTP riêng, giữa hai lượt
-- không có gì trong bộ nhớ, nên chỗ duy nhất giữ được bàn là ở đây.
--
-- Render tự dựng lại tiến trình khá thường xuyên; ghi xuống bảng thì người
-- đang chơi giữa lúc ấy không mất phiên.

CREATE TABLE game_phien (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    nhan_vat      VARCHAR(16) NOT NULL,

    -- Cả bàn 7x7 là một chuỗi 49 ký tự, mỗi ký tự một loại ô. Đọc log cũng
    -- thấy được bàn, và so sánh hai bàn chỉ là so hai chuỗi.
    bang          VARCHAR(64) NOT NULL,

    -- Trạng thái bộ sinh số ngẫu nhiên. Không lưu nó thì phiên nạp lại sẽ rót
    -- ô mới theo một dãy khác, nên bàn sau khi máy chủ khởi động lại không còn
    -- là bàn người chơi đang nhìn.
    hat_giong     BIGINT      NOT NULL,

    diem          INTEGER     NOT NULL DEFAULT 0,
    luot_con_lai  INTEGER     NOT NULL,
    nang_luong    INTEGER     NOT NULL DEFAULT 0,

    -- Phiên đã chốt thì không nhận nước đi nào nữa. Chốt là lúc cộng điểm vào
    -- hạn mức ngày, nên chốt hai lần là cộng hai lần.
    da_chot       BOOLEAN     NOT NULL DEFAULT FALSE,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Tìm phiên đang mở của một người, và dựng lịch sử chơi gần đây.
CREATE INDEX ix_game_phien_nguoi ON game_phien (user_id, created_at DESC);

-- Điểm và lượt thưởng của một người trong một ngày.
--
-- Vì sao gộp theo NGÀY chứ không cộng dồn thành một kho
--
-- Lượt AI thường cũng tính theo ngày (bảng ai_usage_daily), nên lượt thưởng
-- theo ngày là cùng một đơn vị — không phải phát minh thêm khái niệm số dư.
-- Nó cũng tự chặn chuyện dồn: chơi cả tuần rồi tiêu một lúc ba mươi lượt thì
-- hoá đơn Gemini dồn vào một ngày, mà trần ngày lại không thấy gì.
CREATE TABLE game_ngay (
    user_id      UUID    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ngay         DATE    NOT NULL,

    -- Điểm ĐÃ TÍNH vào quy đổi, không phải tổng điểm đã chơi. Chơi quá trần
    -- vẫn chơi được, chỉ là phần vượt không cộng vào đây.
    diem_da_tinh INTEGER NOT NULL DEFAULT 0,

    luot_da_nhan INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (user_id, ngay)
);
