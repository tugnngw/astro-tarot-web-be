package com.exe.astratarot.game;

import java.util.List;

/**
 * Một bậc của dây chuyền: những ô vừa biến mất, và bàn sau khi rót ô mới.
 *
 * <h2>Vì sao trả cả hai ảnh bàn</h2>
 *
 * Giao diện cần diễn ba động tác tách biệt — ô sáng lên rồi tan, ô còn lại rơi
 * xuống, ô mới trượt vào từ trên. Nếu chỉ nhận bàn cuối thì không có cách nào
 * diễn: cả bàn đột ngột đổi, người chơi không thấy mình vừa ăn chuỗi nào.
 *
 * <p>{@code bangSauXoa} là bàn lúc các ô đã mất nhưng chưa ai rơi (có ký tự
 * rỗng), nên trọng lực là thứ trình duyệt tự tính lại được — nó tất định.
 *
 * @param cacOXoa     chỉ số ô bị xoá, đã sắp tăng dần
 * @param bangSauXoa  bàn ngay sau khi xoá, ô mất mang ký tự rỗng
 * @param bangSau     bàn sau khi rơi và rót ô mới
 * @param diem        điểm bậc này, đã nhân hệ số
 * @param heSo        hệ số dây chuyền của bậc này
 * @param bac         bậc thứ mấy trong dây chuyền, đếm từ 1
 */
public record BuocXoa(
        List<Integer> cacOXoa,
        String bangSauXoa,
        String bangSau,
        int diem,
        double heSo,
        int bac) {
}
