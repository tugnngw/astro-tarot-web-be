package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lượt xem đôi: dữ liệu của NGƯỜI THỨ HAI phải vào được lời nhắc.
 *
 * <p>Lỗi đã xảy ra thật: form trên web hỏi đủ tên, ngày sinh, giờ sinh và nơi
 * sinh của hai người, nhưng cả đường dựng lời nhắc chỉ biết một người —
 * {@code buildAstrologyContext} nhận đúng một {@code AstrologyContextDTO}.
 * Người thứ hai được gõ vào rồi biến mất, và thứ trả về là chân dung của
 * riêng người thứ nhất. Người dùng báo: "nó đọc thông tin 2 người mà nó trả
 * về 1".
 *
 * <p>Thứ khiến lỗi này sống lâu là nó KHÔNG hỏng gì cả: không ngoại lệ, không
 * log, lời giải vẫn trôi chảy và vẫn đúng — chỉ đúng về một nửa số người được
 * hỏi. Nên phải khoá bằng test, đừng trông vào mắt người đọc mã.
 */
class PromptBuilderXemDoiTest {

    private PromptBuilderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromptBuilderServiceImpl();
    }

    private AstrologyContextDTO nguoiKimNguu() {
        return AstrologyContextDTO.builder()
                .birthDate(LocalDate.of(2000, 5, 17))
                .birthTime(LocalTime.of(7, 30))
                .birthPlace("Hà Nội, Việt Nam")
                .sunSign("Taurus").element("Earth").modality("Fixed")
                .moonSign("Pisces")
                .build();
    }

    private AstrologyContextDTO nguoiSuTu() {
        return AstrologyContextDTO.builder()
                .birthDate(LocalDate.of(1998, 8, 3))
                .birthTime(LocalTime.of(21, 15))
                .birthPlace("Đà Nẵng, Việt Nam")
                .sunSign("Leo").element("Fire").modality("Fixed")
                .moonSign("Capricorn")
                .build();
    }

    private List<DrawnCardDetailDTO> motLa() {
        return List.of(DrawnCardDetailDTO.builder()
                .cardId(UUID.randomUUID()).cardName("The Lovers")
                .arcanaType("Major Arcana").cardNumber(6)
                .position((short) 0).reversed(false).build());
    }

    private String loiNhac(BuildPromptRequest req) {
        return service.buildLLMRequest(req).getSystemInstruction();
    }

    @Test
    @DisplayName("Có người thứ hai: ngày sinh và cung của CẢ HAI đều vào lời nhắc")
    void haiNguoiThiCoDuHaiBo() {
        String sys = loiNhac(BuildPromptRequest.builder()
                .userQuestion("Hai đứa mình có hợp nhau không?")
                .astrologyContext(nguoiKimNguu())
                .astrologyContextNguoiThuHai(nguoiSuTu())
                .tenNguoiThuHai("Minh")
                .drawnCardDetails(motLa())
                .spreadName("Past-Present-Future")
                .build());

        assertAll(
                // người thứ nhất
                () -> assertTrue(sys.contains("2000-05-17"), "thiếu ngày sinh người thứ nhất"),
                () -> assertTrue(sys.contains("Taurus"), "thiếu cung người thứ nhất"),
                // người thứ hai — đây là phần trước đây rơi mất
                () -> assertTrue(sys.contains("1998-08-03"), "thiếu ngày sinh người thứ hai"),
                () -> assertTrue(sys.contains("Leo"), "thiếu cung người thứ hai"),
                () -> assertTrue(sys.contains("Capricorn"), "thiếu Moon người thứ hai"),
                () -> assertTrue(sys.contains("Đà Nẵng"), "thiếu nơi sinh người thứ hai"),
                () -> assertTrue(sys.contains("Minh"), "thiếu tên người thứ hai"),
                () -> assertTrue(sys.contains("NGƯỜI THỨ HAI"), "không có nhãn tách hai người"),
                () -> assertTrue(sys.contains("NGƯỜI THỨ NHẤT"), "không có nhãn người thứ nhất"));
    }

    @Test
    @DisplayName("Có người thứ hai: lời nhắc buộc luận về quan hệ, không luận một người")
    void haiNguoiThiBatLuanQuanHe() {
        String sys = loiNhac(BuildPromptRequest.builder()
                .userQuestion("Tụi mình hợp không?")
                .astrologyContext(nguoiKimNguu())
                .astrologyContextNguoiThuHai(nguoiSuTu())
                .tenNguoiThuHai("Minh")
                .drawnCardDetails(motLa())
                .build());

        assertAll(
                () -> assertTrue(sys.contains("LƯỢT XEM ĐÔI"), "không tuyên bố là lượt xem đôi"),
                () -> assertTrue(sys.contains("QUAN HỆ"), "không bắt luận về quan hệ"),
                () -> assertTrue(sys.contains("CẢ HAI"), "không bắt nhắc dữ liệu của cả hai"),
                () -> assertTrue(sys.contains("CẶP ĐÔI"), "không nói bài rút cho cặp đôi"));
    }

    @Test
    @DisplayName("Chỉ có tên người thứ hai, không có ngày sinh: vẫn phải biết là xem đôi")
    void coTenMaKhongCoNgaySinh() {
        // Người dùng thường không biết giờ, có khi không biết cả ngày sinh của
        // đối phương. Bỏ qua hẳn thì lời giải lại quay về một người.
        String sys = loiNhac(BuildPromptRequest.builder()
                .userQuestion("Mình với Lan thế nào?")
                .astrologyContext(nguoiKimNguu())
                .astrologyContextNguoiThuHai(null)
                .tenNguoiThuHai("Lan")
                .drawnCardDetails(motLa())
                .build());

        assertAll(
                () -> assertTrue(sys.contains("NGƯỜI THỨ HAI"), "không nhận ra có người thứ hai"),
                () -> assertTrue(sys.contains("Lan"), "mất tên người thứ hai"),
                () -> assertTrue(sys.contains("LƯỢT XEM ĐÔI"), "không tuyên bố là lượt xem đôi"),
                () -> assertTrue(sys.contains("Chưa khai ngày sinh"),
                        "không nói rõ là thiếu ngày sinh của người thứ hai"));
    }

    @Test
    @DisplayName("Một người: lời nhắc không được nhắc gì tới người thứ hai")
    void motNguoiThiKhongDuocLoChuNguoiThuHai() {
        // Lượt một người là gần như mọi lượt. Rò chữ "NGƯỜI THỨ HAI" vào đây
        // là mô hình đi tìm một người không tồn tại rồi hỏi ngược lại.
        String sys = loiNhac(BuildPromptRequest.builder()
                .userQuestion("Công việc của mình sắp tới ra sao?")
                .astrologyContext(nguoiKimNguu())
                .drawnCardDetails(motLa())
                .build());

        assertAll(
                () -> assertFalse(sys.contains("NGƯỜI THỨ HAI"), "rò nhãn người thứ hai"),
                () -> assertFalse(sys.contains("NGƯỜI THỨ NHẤT"), "rò nhãn người thứ nhất"),
                () -> assertFalse(sys.contains("LƯỢT XEM ĐÔI"), "rò phần xem đôi"),
                () -> assertTrue(sys.contains("Taurus"), "mất dữ liệu người duy nhất"));
    }

    @Test
    @DisplayName("Tên toàn khoảng trắng không biến lượt một người thành lượt xem đôi")
    void tenRongThiVanLaMotNguoi() {
        String sys = loiNhac(BuildPromptRequest.builder()
                .userQuestion("Hôm nay thế nào?")
                .astrologyContext(nguoiKimNguu())
                .tenNguoiThuHai("   ")
                .drawnCardDetails(motLa())
                .build());

        assertFalse(sys.contains("NGƯỜI THỨ HAI"),
                "một chuỗi toàn khoảng trắng không phải là một người");
    }

    @Test
    @DisplayName("Xem đôi không có bài: vẫn luận quan hệ, nhưng không dặn gì về lá bài")
    void xemDoiKhongBai() {
        String sys = loiNhac(BuildPromptRequest.builder()
                .userQuestion("Bản đồ sao của hai đứa hợp nhau chỗ nào?")
                .astrologyContext(nguoiKimNguu())
                .astrologyContextNguoiThuHai(nguoiSuTu())
                .tenNguoiThuHai("Minh")
                .drawnCardDetails(List.of())
                .build());

        assertAll(
                () -> assertTrue(sys.contains("LƯỢT XEM ĐÔI")),
                () -> assertTrue(sys.contains("Leo"), "mất cung người thứ hai"),
                () -> assertFalse(sys.contains("CẶP ĐÔI, không chia lá"),
                        "không có bài mà vẫn dặn về cách chia lá"));
    }
}
