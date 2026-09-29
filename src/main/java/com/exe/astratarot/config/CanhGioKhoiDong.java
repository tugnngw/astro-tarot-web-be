package com.exe.astratarot.config;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Canh giờ khởi động: quá hạn mà ứng dụng chưa mở cổng thì in ngăn xếp của mọi
 * luồng ra stdout.
 *
 * <p><b>Vì sao cần.</b> Trên Render, ứng dụng đứng im hoàn toàn sau dòng log
 * cuối cùng — không một dòng nào trong mười một phút, cho tới lúc Render bỏ
 * cuộc với "Port scan timeout reached". Im lặng tuyệt đối như thế nghĩa là
 * luồng {@code main} đang bị chặn ở một lời gọi nào đó, nhưng log không nói
 * được là lời gọi nào: chỗ chặn nằm giữa hai bean mà chẳng bean nào ghi log.
 *
 * <p>Đã dựng lại đúng cái hộp ấy ở máy — 512 MB, 0.15 CPU, cùng bộ cờ JVM, có
 * bật nhật ký gom rác — và ứng dụng lên trong 123 giây, heap chạm 135 MB trên
 * trần 179 MB. Nên không phải bộ nhớ, không phải CPU, không phải cấu hình. Chỗ
 * chặn chỉ có trên Render, và khác biệt còn lại là mấy dịch vụ bên ngoài mà máy
 * làm việc không có: Upstash, Cloudflare, PayOS, Google.
 *
 * <p>Đoán từng cái một thì mỗi lần thử mất mười tám phút deploy. Rẻ hơn nhiều
 * là bắt chính ứng dụng khai ra nó đang đứng ở đâu.
 *
 * <p><b>Khi khởi động bình thường thì đoạn này không tốn gì:</b> một luồng
 * daemon ngủ, tỉnh dậy thấy đã xong thì tự thoát. Không giữ tiến trình sống,
 * không ghi thêm dòng log nào.
 */
public final class CanhGioKhoiDong {

    private CanhGioKhoiDong() {
    }

    private static final AtomicBoolean DA_XONG = new AtomicBoolean(false);

    /**
     * Các mốc kiểm, tính bằng giây kể từ lúc gọi {@link #bat()}.
     *
     * <p>Mốc đầu 180 giây: ở máy, trên đúng hộp 512 MB / 0.15 CPU, ứng dụng lên
     * trong 123 giây. Render chậm hơn chừng gấp đôi ở giai đoạn đầu, nên 180
     * giây đủ rộng để một lần khởi động lành lặn không bao giờ chạm tới.
     *
     * <p>Ba mốc chứ không phải một: một ảnh chụp chỉ cho biết luồng đang ở đâu,
     * ba ảnh cách nhau cho biết nó có nhúc nhích hay không — đứng yên nguyên
     * chỗ qua cả ba lần là chặn thật, chứ không phải đang bò chậm.
     */
    private static final int[] MOC_GIAY = {180, 300, 420};

    /** Gọi ở đầu {@code main()}, trước khi Spring bắt đầu dựng. */
    public static void bat() {
        Thread t = new Thread(CanhGioKhoiDong::chay, "canh-gio-khoi-dong");
        t.setDaemon(true);
        t.start();
    }

    /** Gọi khi ứng dụng đã sẵn sàng. Sau lời gọi này canh giờ im hẳn. */
    public static void xong() {
        DA_XONG.set(true);
    }

    private static void chay() {
        long batDau = System.nanoTime();
        for (int i = 0; i < MOC_GIAY.length; i++) {
            long choToi = MOC_GIAY[i] * 1_000L;
            long daTroi = (System.nanoTime() - batDau) / 1_000_000L;
            try {
                Thread.sleep(Math.max(0, choToi - daTroi));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (DA_XONG.get()) {
                return;
            }
            // Lần đầu in hết mọi luồng để thấy toàn cảnh; những lần sau chỉ in
            // main, vì thứ cần biết là nó có rời khỏi chỗ cũ hay không.
            inNganXep(MOC_GIAY[i], i == 0);
        }
    }

    private static void inNganXep(int giay, boolean tatCa) {
        StringBuilder s = new StringBuilder();
        s.append("\n=============================================================\n");
        s.append("KHOI DONG CHUA XONG SAU ").append(giay).append(" GIAY");
        s.append(tatCa ? " — ngan xep MOI luong:\n" : " — ngan xep luong main:\n");

        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        // Hai tham số true: kèm khoá đang giữ và khoá đang chờ. Nếu main đứng
        // vì một luồng khác ôm khoá thì chỉ hai thông tin ấy mới chỉ ra được,
        // ngăn xếp trần thì không.
        //
        // MAX_VALUE cho độ sâu, và tự in từng khung thay vì dùng
        // ThreadInfo.toString(): toString cắt còn tám khung, mà tám khung đầu
        // của một luồng đang kẹt thường chỉ là mấy lớp socket của JDK — cái
        // cần biết là lớp nào của ứng dụng đã gọi xuống đó.
        for (ThreadInfo ti : mx.dumpAllThreads(true, true, Integer.MAX_VALUE)) {
            if (!tatCa && !"main".equals(ti.getThreadName())) {
                continue;
            }
            s.append('\n').append('"').append(ti.getThreadName()).append("\" Id=")
             .append(ti.getThreadId()).append(' ').append(ti.getThreadState());
            if (ti.getLockName() != null) {
                s.append(" on ").append(ti.getLockName());
            }
            if (ti.getLockOwnerName() != null) {
                s.append(" owned by \"").append(ti.getLockOwnerName())
                 .append("\" Id=").append(ti.getLockOwnerId());
            }
            s.append('\n');
            for (StackTraceElement khung : ti.getStackTrace()) {
                s.append("\tat ").append(khung).append('\n');
            }
            for (java.lang.management.MonitorInfo m : ti.getLockedMonitors()) {
                s.append("\t- dang giu monitor ").append(m).append('\n');
            }
            for (java.lang.management.LockInfo l : ti.getLockedSynchronizers()) {
                s.append("\t- dang giu khoa ").append(l).append('\n');
            }
        }
        s.append("=============================================================\n");

        // In thẳng stdout chứ không qua logger: ngăn xếp dài, và nếu chỗ chặn
        // lại nằm trong chính đường ghi log thì logger cũng đứng theo.
        System.out.println(s);
        System.out.flush();
    }
}
