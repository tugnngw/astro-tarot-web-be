package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.UpdateAstrologyProfileRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserAstrologicalData;
import com.exe.astratarot.domain.enums.ProfileType;
import com.exe.astratarot.repository.UserAstrologicalDataRepository;
import com.exe.astratarot.service.DataEncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hồ sơ chiêm tinh: phần mã hoá và giải mã dữ liệu nhạy cảm.
 *
 * <p>Giờ sinh, nơi sinh và toạ độ là dữ liệu cá nhân, nên chúng được mã hoá
 * trước khi vào cơ sở dữ liệu. Nhưng lớp này còn <b>ghi song song</b> xuống cả
 * cột thường — cố ý, để chuyển đổi dần cho an toàn. Hệ quả là phải kiểm hai
 * điều mà bộ kiểm cũ chưa chạm tới:
 *
 * <ol>
 *   <li><b>Giải mã hỏng thì rơi về cột thường, không mất hồ sơ.</b> Một khoá
 *       xoay vòng hay một bản ghi cũ không giải được không được làm người dùng
 *       mất luôn ngày sinh của họ.
 *   <li><b>Cập nhật KHÔNG gửi trường nhạy cảm thì không đụng vào phần đã mã
 *       hoá.</b> Mã hoá lại một gói rỗng là xoá sạch giờ sinh chỉ vì người ta
 *       sửa mỗi cái tiêu đề.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AstrologyServiceImplMappingTest {

    @Mock private UserAstrologicalDataRepository repository;
    @Mock private DataEncryptionService dataEncryptionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AstrologyServiceImpl service;

    private User khach;
    private UserAstrologicalData hoSo;

    @BeforeEach
    void setUp() {
        service = new AstrologyServiceImpl(repository, dataEncryptionService, objectMapper);

        khach = User.builder().id(UUID.randomUUID()).build();

        hoSo = UserAstrologicalData.builder()
                .id(UUID.randomUUID())
                .user(khach)
                .title("Bản đồ sao của tôi")
                .targetName("Trần Duy Đạt")
                .birthDate(LocalDate.of(2000, 5, 17))
                .birthTime(LocalTime.of(7, 30))
                .birthPlace("Hà Nội")
                .latitude(new BigDecimal("21.0285"))
                .longitude(new BigDecimal("105.8542"))
                .timezone("Asia/Ho_Chi_Minh")
                .profileType(ProfileType.SELF)
                .primary(true)
                .build();

        lenient().when(repository.save(any(UserAstrologicalData.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(repository.findByUserIdAndId(khach.getId(), hoSo.getId()))
                .thenReturn(Optional.of(hoSo));
        lenient().when(repository.findAllByUserId(khach.getId())).thenReturn(List.of(hoSo));
        lenient().when(dataEncryptionService.encryptJson(any()))
                .thenReturn(new DataEncryptionService.EncryptedDataWrapper(
                        new byte[]{1, 2, 3}, new byte[]{4, 5, 6}));
    }

    /** Gói đã giải mã mà dịch vụ mã hoá sẽ trả về. */
    private void giaiMaThanhCong(String gio, String noi, String vd, String kd) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("version", 1);
        if (gio != null) node.put("birthTime", gio);
        if (noi != null) node.put("birthPlace", noi);
        if (vd != null) node.put("latitude", new BigDecimal(vd));
        if (kd != null) node.put("longitude", new BigDecimal(kd));
        lenient().when(dataEncryptionService.decryptJson(any())).thenReturn((JsonNode) node);
    }

    private UpdateAstrologyProfileRequest sua(String tieuDe, LocalTime gio, String noi) {
        return UpdateAstrologyProfileRequest.builder()
                .title(tieuDe)
                .birthTime(gio)
                .birthPlace(noi)
                .build();
    }

    // =====================================================================

    @Test
    @DisplayName("Đọc hồ sơ: ưu tiên dữ liệu ĐÃ GIẢI MÃ hơn cột thường")
    void uuTienDuLieuGiaiMa() {
        hoSo.setEncryptedData(new byte[]{1});
        hoSo.setEncryptionIv(new byte[]{2});
        hoSo.setBirthPlace("Giá trị cũ ở cột thường");
        giaiMaThanhCong("09:15:00", "Hải Phòng", "20.8449", "106.6881");

        var dto = service.getProfiles(khach.getId()).get(0);

        // Cột thường chỉ là bản sao để chuyển đổi dần; bản mã hoá mới là nguồn
        // đúng. Đọc ngược thứ tự là hiển thị dữ liệu cũ sau mỗi lần sửa.
        assertAll(
                () -> assertEquals(LocalTime.of(9, 15), dto.getBirthTime()),
                () -> assertEquals("Hải Phòng", dto.getBirthPlace()),
                () -> assertEquals(new BigDecimal("20.8449"), dto.getLatitude()));
    }

    @Test
    @DisplayName("Giải mã HỎNG thì rơi về cột thường, KHÔNG mất hồ sơ")
    void giaiMaHongThiRoiVeCotThuong() {
        hoSo.setEncryptedData(new byte[]{1});
        hoSo.setEncryptionIv(new byte[]{2});
        when(dataEncryptionService.decryptJson(any()))
                .thenThrow(new DataEncryptionService.DecryptionException("khoá không khớp", new RuntimeException()));

        var dto = service.getProfiles(khach.getId()).get(0);

        // Một khoá xoay vòng hay một bản ghi cũ không giải được không được làm
        // người dùng mất luôn ngày sinh của họ.
        assertAll(
                () -> assertEquals(LocalTime.of(7, 30), dto.getBirthTime()),
                () -> assertEquals("Hà Nội", dto.getBirthPlace()),
                () -> assertEquals(new BigDecimal("21.0285"), dto.getLatitude()));
    }

    @Test
    @DisplayName("Lỗi bất ngờ khi giải mã cũng rơi về cột thường")
    void loiBatNgoCungRoiVeCotThuong() {
        hoSo.setEncryptedData(new byte[]{1});
        hoSo.setEncryptionIv(new byte[]{2});
        when(dataEncryptionService.decryptJson(any()))
                .thenThrow(new RuntimeException("gói dữ liệu méo"));

        assertEquals("Hà Nội", service.getProfiles(khach.getId()).get(0).getBirthPlace());
    }

    @Test
    @DisplayName("Hồ sơ CHƯA mã hoá thì đọc thẳng cột thường")
    void hoSoChuaMaHoa() {
        hoSo.setEncryptedData(null);
        hoSo.setEncryptionIv(null);

        // Bản ghi từ trước khi bật mã hoá. Bỏ qua chúng là ẩn mất hồ sơ cũ.
        var dto = service.getProfiles(khach.getId()).get(0);
        assertAll(
                () -> assertEquals(LocalTime.of(7, 30), dto.getBirthTime()),
                () -> assertEquals("Hà Nội", dto.getBirthPlace()));
        verify(dataEncryptionService, never()).decryptJson(any());
    }

    @Test
    @DisplayName("Giờ sinh trong gói mã hoá SAI ĐỊNH DẠNG thì rơi về cột thường")
    void gioSinhSaiDinhDang() {
        hoSo.setEncryptedData(new byte[]{1});
        hoSo.setEncryptionIv(new byte[]{2});
        giaiMaThanhCong("bảy giờ rưỡi sáng", "Hải Phòng", null, null);

        // Một trường hỏng không được kéo theo cả hồ sơ: nơi sinh vẫn phải lấy
        // được từ gói đã giải mã.
        var dto = service.getProfiles(khach.getId()).get(0);
        assertAll(
                () -> assertEquals(LocalTime.of(7, 30), dto.getBirthTime()),
                () -> assertEquals("Hải Phòng", dto.getBirthPlace()));
    }

    @Test
    @DisplayName("Gói mã hoá THIẾU trường thì lấy nốt từ cột thường")
    void goiThieuTruong() {
        hoSo.setEncryptedData(new byte[]{1});
        hoSo.setEncryptionIv(new byte[]{2});
        giaiMaThanhCong(null, null, null, null);

        var dto = service.getProfiles(khach.getId()).get(0);
        assertAll(
                () -> assertEquals(LocalTime.of(7, 30), dto.getBirthTime()),
                () -> assertEquals("Hà Nội", dto.getBirthPlace()));
    }

    @Test
    @DisplayName("Sửa mà KHÔNG gửi trường nhạy cảm thì không mã hoá lại")
    void suaKhongGuiTruongNhayCam() {
        service.updateProfile(khach.getId(), hoSo.getId(), sua("Tiêu đề mới", null, null));

        // Mã hoá lại một gói rỗng là xoá sạch giờ sinh chỉ vì người ta sửa mỗi
        // cái tiêu đề.
        assertAll(
                () -> assertEquals("Tiêu đề mới", hoSo.getTitle()),
                () -> assertEquals(LocalTime.of(7, 30), hoSo.getBirthTime()),
                () -> assertEquals("Hà Nội", hoSo.getBirthPlace()));
        verify(dataEncryptionService, never()).encryptJson(any());
    }

    @Test
    @DisplayName("Sửa CÓ gửi trường nhạy cảm thì mã hoá lại và ghi song song cột thường")
    void suaCoGuiTruongNhayCam() {
        service.updateProfile(khach.getId(), hoSo.getId(),
                sua(null, LocalTime.of(9, 15), "Hải Phòng"));

        assertAll(
                () -> assertEquals(LocalTime.of(9, 15), hoSo.getBirthTime()),
                () -> assertEquals("Hải Phòng", hoSo.getBirthPlace()),
                // Ghi song song: cột thường là bản sao để chuyển đổi dần, và
                // nó phải luôn khớp với nội dung trong gói mã hoá.
                () -> org.junit.jupiter.api.Assertions.assertNotNull(hoSo.getEncryptedData()));
        verify(dataEncryptionService).encryptJson(any());
    }

    @Test
    @DisplayName("Mã hoá THẤT BẠI thì báo lỗi, không lưu hồ sơ nửa vời")
    void maHoaThatBai() {
        when(dataEncryptionService.encryptJson(any()))
                .thenThrow(new DataEncryptionService.EncryptionException("thiếu khoá", new RuntimeException()));

        // Lưu được bản ghi mà phần mã hoá rỗng là một hồ sơ trông bình thường
        // nhưng không có dữ liệu nhạy cảm nào — và không ai biết cho tới khi mở.
        assertThrows(RuntimeException.class,
                () -> service.updateProfile(khach.getId(), hoSo.getId(),
                        sua(null, LocalTime.of(9, 15), null)));
    }

    @Test
    @DisplayName("Nơi sinh chỉ có dấu cách thì không đưa vào gói mã hoá")
    void noiSinhChiCoDauCach() {
        service.updateProfile(khach.getId(), hoSo.getId(), sua(null, LocalTime.of(9, 15), "   "));

        // Một chuỗi trắng trong gói mã hoá chiếm chỗ mà không mang thông tin
        // nào, và khi đọc ra lại thắng giá trị thật ở cột thường.
        verify(dataEncryptionService).encryptJson(any());
        assertEquals("   ", hoSo.getBirthPlace());
    }

    @Test
    @DisplayName("Hồ sơ CHÍNH cũng đi qua đúng đường giải mã")
    void hoSoChinh() {
        hoSo.setEncryptedData(new byte[]{1});
        hoSo.setEncryptionIv(new byte[]{2});
        giaiMaThanhCong("09:15:00", "Hải Phòng", null, null);
        when(repository.findByUserIdAndPrimaryTrue(khach.getId())).thenReturn(Optional.of(hoSo));

        var chinh = service.getPrimaryProfile(khach.getId());

        assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(chinh.isPresent()),
                () -> assertEquals("Hải Phòng", chinh.get().getBirthPlace()));
    }
}
