package com.exe.astratarot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Tự gọi chính mình để Render đừng cho dịch vụ đi ngủ.
 *
 * <p>Gói free của Render tắt dịch vụ sau 15 phút không có lượt gọi nào, và lần
 * gọi kế tiếp phải chờ nó khởi động lại — thường 50 giây trở lên. Đó là nguyên
 * nhân của "danh sách lúc tải được lúc không", và của cả lần đăng nhập hỏng mà
 * không hiểu vì sao.
 *
 * <p><b>Vì sao không để GitHub Actions lo việc này.</b> Đã có sẵn một workflow
 * {@code keep-warm.yml} làm đúng việc đó. Sau bốn tiếng nằm trên nhánh mặc
 * định, nó chạy được <b>không lần nào</b>: lịch {@code schedule} của GitHub chỉ
 * là mong muốn, và ở gói free thì lượt chạy bị hoãn hoặc bỏ hẳn là chuyện bình
 * thường. Giữ workflow đó lại làm lớp dự phòng, nhưng thứ chống ngủ thật sự
 * phải nằm trong chính ứng dụng.
 *
 * <p>Phải gọi qua <b>URL công khai</b> chứ không phải localhost: Render tính
 * "có ai dùng không" theo lượt đi qua bộ định tuyến của họ. Một lời gọi vòng
 * trong máy không ai nhìn thấy.
 *
 * <p>Đây chỉ giữ cho dịch vụ khỏi ngủ, KHÔNG đánh thức được nó: đã ngủ rồi thì
 * làm gì còn ai chạy mà gọi. Lần đầu sau mỗi lần deploy vẫn do người dùng thật
 * đánh thức — hoặc do workflow dự phòng kia nếu nó chịu chạy.
 *
 * <p>Hạn mức: bản free của Render cho 750 giờ chạy mỗi tháng trên cả tài khoản,
 * mà một tháng có ~730 giờ. Tức vừa đủ giữ thức MỘT dịch vụ. Có dịch vụ thứ hai
 * luôn thức là vượt hạn mức.
 */
@Slf4j
@Component
public class KeepAwakeJob {

    /**
     * Render tự tiêm biến này vào mọi service, nên không phải khai báo gì thêm
     * trên bảng điều khiển. Chạy ở máy thì biến rỗng và cả lớp này nằm im —
     * đúng như mong muốn, không ai cần tự gọi mình khi đang chạy localhost.
     */
    @Value("${RENDER_EXTERNAL_URL:}")
    private String publicUrl;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Mỗi 10 phút — phải nhỏ hơn ngưỡng 15 phút của Render, và chừa biên cho
     * một lượt lỡ nhịp.
     */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT10M")
    public void giuThuc() {
        if (publicUrl == null || publicUrl.isBlank()) return;

        String url = publicUrl.replaceAll("/$", "") + "/actuator/health";
        try {
            HttpResponse<Void> res = http.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(20))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            log.debug("Tự ping giữ thức: {} -> {}", url, res.statusCode());
        } catch (InterruptedException e) {
            // Đặt lại cờ ngắt rồi thôi. CHỈ ở đây mới được gọi interrupt():
            // gọi nó trong khối bắt Exception chung sẽ ngắt luôn luồng lập
            // lịch vì bất kỳ lỗi mạng vặt nào, và mọi lượt chạy sau đó chết
            // theo — kể cả việc tự chốt buổi xem.
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Không sao cả: hỏng một lượt ping thì cùng lắm là dịch vụ đi ngủ,
            // chứ không ảnh hưởng gì tới người đang dùng. Để ở DEBUG cho khỏi
            // rác log mỗi lần mạng chớp.
            log.debug("Lượt ping giữ thức hỏng ({}): {}", url, e.getMessage());
        }
    }
}
