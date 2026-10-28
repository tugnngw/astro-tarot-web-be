package com.exe.astratarot.game;

/**
 * Bộ sinh số ngẫu nhiên có thể chép lại được, mang theo trạng thái của chính nó.
 *
 * <h2>Vì sao không dùng {@link java.util.Random}</h2>
 *
 * Bàn chơi nằm ở máy chủ nhưng mỗi nước đi là một lượt gọi HTTP riêng, nên giữa
 * hai nước đi không có gì trong bộ nhớ: trạng thái phải nằm trong cơ sở dữ liệu.
 * {@code Random} không cho đọc hạt giống hiện tại ra, nên không lưu được —
 * phiên chơi sau khi nạp lại sẽ rót ô mới theo một dãy khác, và người chơi mất
 * phiên nếu máy chủ khởi động lại giữa lúc đang chơi (Render tự dựng lại tiến
 * trình khá thường xuyên).
 *
 * <p>Thuật toán là LCG 64 bit với bộ hằng số của Knuth. Nó KHÔNG an toàn về mật
 * mã, và ở đây không cần: biết trước ô sắp rót xuống cũng chẳng cho ai điểm nào,
 * vì điểm do máy chủ tính từ nước đi thật.
 */
public final class MayNgauNhien {

    private long hatGiong;

    public MayNgauNhien(long hatGiong) {
        this.hatGiong = hatGiong;
    }

    /** Hạt giống hiện tại, để lưu xuống cơ sở dữ liệu sau mỗi nước đi. */
    public long hatGiong() {
        return hatGiong;
    }

    /**
     * Một số nguyên trong khoảng {@code [0, n)}.
     *
     * <p>Lấy 32 bit CAO chứ không phải bit thấp: ở LCG các bit thấp có chu kỳ
     * rất ngắn — bit thấp nhất chỉ lật qua lật lại 0,1,0,1. Dùng nó để chọn
     * loại ô thì bàn chơi sẽ ra những dải xen kẽ đều đặn mà mắt nhìn thấy ngay.
     */
    public int toi(int n) {
        if (n <= 0) throw new IllegalArgumentException("n phải dương, nhận " + n);
        hatGiong = hatGiong * 6364136223846793005L + 1442695040888963407L;
        int cao = (int) (hatGiong >>> 32);
        return Math.floorMod(cao, n);
    }
}
