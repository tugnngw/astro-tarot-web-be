package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.PaymentTransaction;
import com.exe.astratarot.service.VietQrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mã QR chuyển khoản của KHÁCH — điền sẵn số tiền và nội dung.
 *
 * <p>Không có mã thì khách phải gõ tay ba thứ: số tài khoản, số tiền, và một
 * mã đối soát chữ-số lẫn lộn. Gõ sai số tiền thì giao dịch không khớp và treo
 * lại chờ đối soát tay; gõ sai mã đối soát thì tiền tới nơi mà không ai biết
 * nó thuộc lượt đặt nào.
 *
 * <p>Cả bốn nhánh ở đây đều IM LẶNG — không nhánh nào báo lỗi ra màn hình.
 * Thiếu cấu hình hay dựng chuỗi hỏng đều chỉ làm mã biến mất, và giao diện
 * lặng lẽ quay về cách gõ tay. Nên không có phép kiểm thì không cách nào biết
 * nó còn chạy đúng.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QR chuyển khoản cho khách")
class PaymentServiceImplQrTest {

    @Mock
    private VietQrService vietQrService;

    private PaymentServiceImpl service;
    private PaymentTransaction giaoDich;

    @BeforeEach
    void setUp() {
        // Chỉ dựng đúng những gì nhánh QR chạm tới. Constructor của
        // PaymentServiceImpl nhận nhiều thành phần, nhưng phép kiểm này không
        // đi qua chúng — nên tiêm thẳng qua phản chiếu thay vì dựng cả bộ.
        service = new PaymentServiceImpl(
                null, null, null, null, null, null, null, null, vietQrService);

        giaoDich = new PaymentTransaction();
        giaoDich.setId(UUID.randomUUID());
        giaoDich.setAmount(180_000L);
        giaoDich.setExternalTransactionId("AT7K2M9P");
    }

    private void khaiNganHang(String bin, String soTaiKhoan) {
        ReflectionTestUtils.setField(service, "bankBin", bin);
        ReflectionTestUtils.setField(service, "bankAccountNumber", soTaiKhoan);
    }

    @Test
    @DisplayName("Có cấu hình thì dựng mã kèm ĐÚNG số tiền và ĐÚNG mã đối soát")
    void dungMaKemTienVaNoiDung() {
        khaiNganHang("970436", "0123456789");
        when(vietQrService.dungChuoi(anyString(), anyString(), anyLong(), anyString()))
                .thenReturn("00020101021238...6304ABCD");

        String ma = service.maQrChuyenKhoan(giaoDich);

        assertEquals("00020101021238...6304ABCD", ma);
        // Đây là toàn bộ lý do mã này tồn tại: quét xong là ứng dụng ngân hàng
        // đã có sẵn số tiền và nội dung, khách không gõ gì nữa. Truyền thiếu
        // một trong hai thì mã vẫn quét được và vẫn trông đúng — chỉ là khách
        // lại phải gõ tay đúng cái thứ dễ sai nhất.
        verify(vietQrService).dungChuoi("970436", "0123456789", 180_000L, "AT7K2M9P");
    }

    @Test
    @DisplayName("Số tài khoản có khoảng trắng thừa thì cắt trước khi dựng")
    void catKhoangTrangThua() {
        khaiNganHang(" 970436 ", " 0123456789 ");
        when(vietQrService.dungChuoi(anyString(), anyString(), anyLong(), anyString()))
                .thenReturn("ma");

        service.maQrChuyenKhoan(giaoDich);

        // Biến môi trường dán từ chỗ khác rất hay dính khoảng trắng ở đuôi, và
        // một số tài khoản thừa dấu cách là một mã QR trỏ tới hư không.
        verify(vietQrService).dungChuoi("970436", "0123456789", 180_000L, "AT7K2M9P");
    }

    @Test
    @DisplayName("Chưa cấu hình ngân hàng thì KHÔNG dựng mã, và không gọi tới")
    void chuaCauHinhThiKhongDungMa() {
        assertAll(
                () -> {
                    khaiNganHang(null, "0123456789");
                    assertNull(service.maQrChuyenKhoan(giaoDich), "thiếu BIN");
                },
                () -> {
                    khaiNganHang("   ", "0123456789");
                    assertNull(service.maQrChuyenKhoan(giaoDich), "BIN toàn dấu cách");
                },
                () -> {
                    khaiNganHang("970436", null);
                    assertNull(service.maQrChuyenKhoan(giaoDich), "thiếu số tài khoản");
                },
                () -> {
                    // Giá trị mặc định khi chưa gắn biến môi trường. Dựng QR
                    // từ chuỗi này ra một mã trỏ tới một tài khoản không tồn
                    // tại — tệ hơn hẳn việc không có mã.
                    khaiNganHang("970436", "Chưa cấu hình");
                    assertNull(service.maQrChuyenKhoan(giaoDich), "số tài khoản mặc định");
                });
        verify(vietQrService, never()).dungChuoi(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Dựng chuỗi hỏng thì bỏ mã, KHÔNG làm hỏng cả màn hướng dẫn")
    void dungChuoiHongThiBoMa() {
        khaiNganHang("970436", "0123456789");
        when(vietQrService.dungChuoi(anyString(), anyString(), anyLong(), anyString()))
                .thenThrow(new IllegalArgumentException("BIN sai định dạng"));

        // Không có mã thì vẫn chuyển khoản được bằng tay. Không có màn hướng
        // dẫn thì không — nên lỗi ở đây phải dừng lại ở đây.
        assertNull(service.maQrChuyenKhoan(giaoDich));
    }
}
