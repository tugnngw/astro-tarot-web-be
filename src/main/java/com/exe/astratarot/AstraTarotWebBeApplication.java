package com.exe.astratarot;

import com.exe.astratarot.config.CanhGioKhoiDong;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableScheduling;

// Bật lịch chạy nền cho BookingSettlementJob: tiền của những buổi xem đã
// qua mà Reader quên bấm hoàn tất sẽ tự được chốt.
@EnableScheduling
@SpringBootApplication
public class AstraTarotWebBeApplication {

    public static void main(String[] args) {
        // Đặt trước khi Spring bắt đầu dựng: nếu khởi động treo thì chính ứng
        // dụng in ra nó đang đứng ở đâu, thay vì để lại một khoảng log trống.
        CanhGioKhoiDong.bat();

        SpringApplication app = new SpringApplication(AstraTarotWebBeApplication.class);
        app.addListeners((ApplicationListener<ApplicationReadyEvent>) e -> CanhGioKhoiDong.xong());
        app.run(args);
    }

}
