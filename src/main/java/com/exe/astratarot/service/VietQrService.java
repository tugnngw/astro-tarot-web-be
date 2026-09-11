package com.exe.astratarot.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Dựng chuỗi mã QR chuyển khoản theo chuẩn VietQR (EMVCo QR + Napas).
 *
 * <p>Vì sao tự dựng thay vì gọi dịch vụ sinh ảnh bên ngoài: chuỗi này là dữ
 * liệu thuần, không cần khoá, không cần mạng, và không phụ thuộc vào một bên
 * thứ ba có thể ngừng hoạt động. Ứng dụng ngân hàng đọc chính chuỗi này; ảnh
 * QR chỉ là cách chở nó đi, và phía giao diện tự vẽ được.
 *
 * <p>Cấu trúc là các bản ghi TLV lồng nhau: mỗi bản ghi gồm hai chữ số ID, hai
 * chữ số độ dài, rồi tới nội dung. Sai một chữ số độ dài là hỏng cả mã, nên độ
 * dài luôn được tính từ chính nội dung chứ không viết tay.
 */
@Service
public class VietQrService {

    /** Mã định danh của Napas trong trường "thông tin đơn vị thụ hưởng". */
    private static final String GUID_NAPAS = "A000000727";

    /** Chuyển khoản tới TÀI KHOẢN ngân hàng (khác QRIBFTTC là tới thẻ nội địa). */
    private static final String DICH_VU_CHUYEN_TOI_TAI_KHOAN = "QRIBFTTA";

    private static final String TIEN_VND = "704";
    private static final String QUOC_GIA = "VN";

    /**
     * @param bin     mã BIN ngân hàng thụ hưởng, 6 chữ số (970436 = Vietcombank)
     * @param soTaiKhoan số tài khoản người nhận
     * @param soTien  số tiền; null hoặc &lt;= 0 thì dựng mã tĩnh để người chuyển tự nhập
     * @param noiDung nội dung chuyển khoản, sẽ bị lược bỏ dấu và ký tự lạ
     * @return chuỗi EMV hoàn chỉnh kèm CRC, sẵn sàng vẽ thành QR
     */
    public String dungChuoi(String bin, String soTaiKhoan, Long soTien, String noiDung) {
        if (bin == null || bin.isBlank() || soTaiKhoan == null || soTaiKhoan.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã ngân hàng hoặc số tài khoản");
        }
        boolean coSoTien = soTien != null && soTien > 0;

        String thongTinThuHuong = tlv("00", bin) + tlv("01", soTaiKhoan.trim());
        String donViThuHuong =
                tlv("00", GUID_NAPAS)
                        + tlv("01", thongTinThuHuong)
                        + tlv("02", DICH_VU_CHUYEN_TOI_TAI_KHOAN);

        StringBuilder sb = new StringBuilder();
        sb.append(tlv("00", "01"));
        // 11 = mã dùng nhiều lần, 12 = mã một lần. Có sẵn số tiền thì là mã một
        // lần: quét lại lần nữa sẽ chuyển đúng khoản đó thêm lần nữa.
        sb.append(tlv("01", coSoTien ? "12" : "11"));
        sb.append(tlv("38", donViThuHuong));
        sb.append(tlv("53", TIEN_VND));
        if (coSoTien) {
            sb.append(tlv("54", String.valueOf(soTien)));
        }
        sb.append(tlv("58", QUOC_GIA));

        String moTa = lamSach(noiDung);
        if (!moTa.isEmpty()) {
            sb.append(tlv("62", tlv("08", moTa)));
        }

        // CRC được tính trên TOÀN BỘ chuỗi đã có sẵn "6304" ở cuối — phần đầu
        // của chính bản ghi CRC cũng nằm trong dữ liệu được tính.
        sb.append("6304");
        sb.append(crc16(sb.toString()));
        return sb.toString();
    }

    private static String tlv(String id, String giaTri) {
        return id + String.format("%02d", giaTri.length()) + giaTri;
    }

    /**
     * Bỏ dấu tiếng Việt và mọi ký tự ngoài chữ–số–khoảng trắng.
     *
     * <p>Nội dung chuyển khoản đi qua hệ thống liên ngân hàng vốn chỉ nhận ASCII;
     * để nguyên dấu thì tuỳ ngân hàng mà thành dấu hỏi hoặc bị cắt ngang.
     */
    private static String lamSach(String s) {
        if (s == null) return "";
        String khongDau = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D');
        String chiAscii = khongDau.replaceAll("[^A-Za-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // Trường nội dung của VietQR tối đa 99 ký tự; cắt sớm hơn cho chắc và
        // vì không ai đọc hết một dòng dài thế trong ứng dụng ngân hàng.
        return chiAscii.length() > 50 ? chiAscii.substring(0, 50).trim() : chiAscii;
    }

    /**
     * CRC-16/CCITT-FALSE: đa thức 0x1021, giá trị đầu 0xFFFF, không đảo bit.
     *
     * <p>Đúng biến thể mà EMVCo quy định. Dùng nhầm một biến thể khác (ví dụ
     * CRC-16/ARC) vẫn ra bốn chữ số trông rất hợp lệ, nhưng mọi ứng dụng ngân
     * hàng sẽ từ chối mã — một lỗi không có triệu chứng nào ngoài "quét không
     * được".
     */
    static String crc16(String duLieu) {
        int crc = 0xFFFF;
        for (byte b : duLieu.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x1021) : (crc << 1);
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
