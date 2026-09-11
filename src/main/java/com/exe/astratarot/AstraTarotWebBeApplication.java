package com.exe.astratarot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Bật lịch chạy nền cho BookingSettlementJob: tiền của những buổi xem đã
// qua mà Reader quên bấm hoàn tất sẽ tự được chốt.
@EnableScheduling
@SpringBootApplication
public class AstraTarotWebBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AstraTarotWebBeApplication.class, args);
    }

}
