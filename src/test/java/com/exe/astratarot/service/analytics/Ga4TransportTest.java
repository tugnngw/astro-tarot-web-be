package com.exe.astratarot.service.analytics;

import com.google.analytics.data.v1beta.BetaAnalyticsDataSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đường truyền tới Google Analytics Data API phải là REST, không phải gRPC.
 *
 * <p>Vì sao đáng một bài test riêng: kênh gRPC nạp thư viện SSL viết bằng C
 * (netty-tcnative) ngay lúc khởi tạo, và trên Render thư viện ấy chết hẳn
 * tiến trình — không ném ngoại lệ, không log gì thêm, chỉ:
 *
 * <pre>
 *   SIGSEGV ... netty_internal_tcnative_SSLContext_JNI_OnLoad
 *   Exited with status 139
 * </pre>
 *
 * <p>Chết ở tầng native thì {@code catch} không đỡ được: CẢ máy chủ sập theo,
 * không riêng gì phần Analytics. Đã xảy ra thật ngay lần đầu đặt khoá GA4 lên
 * Render, và cách duy nhất cứu production lúc ấy là gỡ biến khoá ra.
 *
 * <p>Nên chỗ chọn đường truyền không được phép đổi lại bằng một lần "dọn cho
 * giống tài liệu mẫu của Google" — tài liệu mẫu dùng {@code newBuilder()}.
 */
class Ga4TransportTest {

    @Test
    @DisplayName("newHttpJsonBuilder cho đường truyền REST, không dựng kênh gRPC")
    void dungRestChuKhongDungGrpc() throws IOException {
        BetaAnalyticsDataSettings rest = BetaAnalyticsDataSettings.newHttpJsonBuilder().build();

        assertThat(rest.getTransportChannelProvider().getTransportName())
                .isEqualTo("httpjson");
    }

    @Test
    @DisplayName("bản mặc định của Google là gRPC — đúng cái phải tránh")
    void banMacDinhVanLaGrpc() throws IOException {
        // Giữ lại để đối chiếu: nếu một ngày Google đổi mặc định sang REST thì
        // test này đỏ, và lúc ấy chú thích dài trong DefaultGa4ClientProvider
        // nên được viết lại cho đúng thực tế mới.
        BetaAnalyticsDataSettings grpc = BetaAnalyticsDataSettings.newBuilder().build();

        assertThat(grpc.getTransportChannelProvider().getTransportName())
                .isEqualTo("grpc");
    }
}
