package com.exe.astratarot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

/**
 * Ghi lại hạn mức bộ nhớ THẬT SỰ đang áp dụng, một dòng, lúc khởi động.
 *
 * <p>Vì sao đáng có: ứng dụng từng ném {@code OutOfMemoryError: Metaspace}
 * giữa lúc phục vụ request, và triệu chứng duy nhất người dùng thấy là màn
 * thống kê của quản trị viên trả 500. Lần ra được nguyên nhân phải đi qua log
 * Render, tìm stack trace, rồi mới đọc tới biến môi trường.
 *
 * <p>Hạn mức lại đến từ ba nguồn chồng lên nhau — {@code JAVA_TOOL_OPTIONS} của
 * Render, {@code JAVA_OPTS} trong render.yaml, và cờ ở ENTRYPOINT — nên "giá
 * trị đang chạy là bao nhiêu" không đọc được từ bất kỳ file nào. Chỉ có JVM
 * biết, nên hỏi thẳng JVM.
 */
@Slf4j
@Component
public class MemoryBudgetLogger {

    @EventListener(ApplicationReadyEvent.class)
    public void ghiHanMuc() {
        long heapToiDa = Runtime.getRuntime().maxMemory();

        long metaToiDa = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(b -> "Metaspace".equals(b.getName()))
                .map(MemoryPoolMXBean::getUsage)
                .filter(u -> u != null && u.getMax() > 0)
                .mapToLong(u -> u.getMax())
                .findFirst()
                .orElse(-1);

        long metaDangDung = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(b -> "Metaspace".equals(b.getName()))
                .map(MemoryPoolMXBean::getUsage)
                .filter(u -> u != null)
                .mapToLong(u -> u.getUsed())
                .findFirst()
                .orElse(-1);

        log.info("Hạn mức bộ nhớ: heap tối đa {} MB, metaspace tối đa {}, đang dùng {} MB",
                heapToiDa / 1024 / 1024,
                metaToiDa < 0 ? "không giới hạn" : (metaToiDa / 1024 / 1024) + " MB",
                metaDangDung < 0 ? "?" : metaDangDung / 1024 / 1024);

        // Metaspace lúc vừa khởi động đã chiếm quá ba phần tư hạn mức thì gần
        // như chắc chắn sẽ vỡ khi có tải: số lớp còn tăng tiếp khi các đường
        // dẫn mã ít dùng được nạp lần đầu.
        if (metaToiDa > 0 && metaDangDung > metaToiDa * 3 / 4) {
            log.warn("Metaspace đã dùng {}/{} MB ngay khi khởi động. Nới "
                            + "-XX:MaxMetaspaceSize trước khi nó vỡ giữa lúc phục vụ.",
                    metaDangDung / 1024 / 1024, metaToiDa / 1024 / 1024);
        }
    }
}
