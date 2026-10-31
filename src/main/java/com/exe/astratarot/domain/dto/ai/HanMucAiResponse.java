package com.exe.astratarot.domain.dto.ai;

import lombok.*;

/**
 * Hạn mức Tarot AI của một người trong HÔM NAY, đã cộng đủ mọi nguồn.
 *
 * <h2>Vì sao cần một DTO riêng thay vì để client tự tính</h2>
 *
 * Trước đây giao diện tự dựng lại con số này: lấy danh sách gói đang hoạt
 * động rồi {@code max(dailyQuotaSnapshot)}, không có gói thì mặc định 3. Đó là
 * một bản sao của luật nằm trong {@code getQuotaForToday}, và hai bản đã lệch
 * nhau ngay khi mini game bắt đầu thưởng lượt: máy chủ CỘNG phần thưởng vào,
 * giao diện thì không biết gì về nó.
 *
 * Hậu quả thật, người dùng báo lại ngày 31/10/2026: chơi game được thưởng 3
 * lượt, nhưng huy hiệu trên trang Tarot vẫn ghi "AI: 3/3 lượt". Lượt thưởng
 * DÙNG được — {@code canUseAI} và {@code recordAIUsage} đều đọc đúng con số —
 * chỉ là không chỗ nào nói cho người ta biết là họ có.
 *
 * Nên con số này chỉ có một nơi được tính, và nơi ấy là máy chủ. Client nhận
 * về rồi hiện ra, không cộng trừ gì nữa.
 *
 * <h2>Vì sao tách riêng {@code tuGoi} và {@code tuGame}</h2>
 *
 * Để màn hình nói được "3 lượt từ gói, cộng 3 lượt bạn vừa chơi game". Gộp
 * thành một con số thì người chơi xong một ván không thấy công của mình ở đâu,
 * và đó đúng là cái họ đi tìm.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HanMucAiResponse {

    /** Tổng hạn mức hôm nay = {@link #tuGoi} + {@link #tuGame}. */
    private int tongHanMuc;

    /** Phần đến từ gói đang hoạt động, hoặc gói miễn phí nếu không có gói nào. */
    private int tuGoi;

    /** Phần thưởng kiếm được từ mini game trong hôm nay. */
    private int tuGame;

    /** Số lượt đã dùng hôm nay. */
    private int daDung;

    /** Còn lại bao nhiêu. Không bao giờ âm. */
    private int conLai;
}
