package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.reader.CreateAvailabilityRequest;
import com.exe.astratarot.domain.entity.ReaderAvailability;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.AvailabilityConflictException;
import com.exe.astratarot.exception.InvalidAvailabilityTimeException;
import com.exe.astratarot.exception.ReaderNotVerifiedException;
import com.exe.astratarot.repository.ReaderAvailabilityRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lịch làm việc hằng tuần của Reader. Lớp này trước đây phủ 0,0%.
 *
 * <p>Thứ đáng kiểm là <b>chồng lấn khung giờ</b>. Hai khung chồng nhau không
 * báo lỗi ngay thì chúng sẽ sinh ra khung trống trùng nhau ở trang đặt lịch, và
 * hai khách đặt trúng cùng một giờ của cùng một Reader — lỗi chỉ lộ ra đúng lúc
 * hai người cùng chờ trong phòng chat.
 *
 * <p>Điểm thứ hai: xoá là <b>tắt</b> chứ không xoá dòng. Khung giờ đã có lịch
 * hẹn gắn vào; xoá thật là làm mồ côi những lịch ấy.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReaderAvailabilityServiceImplTest {

    @Mock private ReaderAvailabilityRepository availabilityRepository;
    @Mock private ReaderProfileRepository readerProfileRepository;

    private ReaderAvailabilityServiceImpl service;

    private User readerUser;
    private ReaderProfile reader;

    /** Thứ Hai theo quy ước của bảng: 0 = Chủ nhật. */
    private static final short THU_HAI = 1;

    @BeforeEach
    void setUp() {
        service = new ReaderAvailabilityServiceImpl(availabilityRepository, readerProfileRepository);

        readerUser = new User();
        readerUser.setId(UUID.randomUUID());

        reader = ReaderProfile.builder().id(UUID.randomUUID()).user(readerUser).build();

        lenient().when(readerProfileRepository.findByUserId(readerUser.getId()))
                .thenReturn(Optional.of(reader));
        lenient().when(availabilityRepository.findByReaderId(reader.getId()))
                .thenReturn(List.of());
        lenient().when(availabilityRepository.save(any(ReaderAvailability.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateAvailabilityRequest yeuCau(short thu, String tu, String den) {
        CreateAvailabilityRequest r = new CreateAvailabilityRequest();
        r.setDayOfWeek(thu);
        r.setStartTime(LocalTime.parse(tu));
        r.setEndTime(LocalTime.parse(den));
        return r;
    }

    private ReaderAvailability khung(short thu, String tu, String den, boolean bat) {
        return ReaderAvailability.builder()
                .id(UUID.randomUUID())
                .reader(reader)
                .dayOfWeek(thu)
                .startTime(LocalTime.parse(tu))
                .endTime(LocalTime.parse(den))
                .active(bat)
                .build();
    }

    // =====================================================================

    @Test
    @DisplayName("Khai khung giờ mới: bật sẵn và gắn đúng Reader")
    void khaiKhungGioMoi() {
        service.create(readerUser.getId(), yeuCau(THU_HAI, "09:00", "12:00"));

        ArgumentCaptor<ReaderAvailability> bat = ArgumentCaptor.forClass(ReaderAvailability.class);
        verify(availabilityRepository).save(bat.capture());
        assertAll(
                () -> assertEquals(reader, bat.getValue().getReader()),
                () -> assertEquals(THU_HAI, bat.getValue().getDayOfWeek()),
                () -> assertEquals(LocalTime.of(9, 0), bat.getValue().getStartTime()),
                () -> assertTrue(bat.getValue().getActive()));
    }

    @Test
    @DisplayName("Giờ kết thúc không sau giờ bắt đầu thì bị chặn")
    void gioKetThucKhongHopLe() {
        assertAll(
                () -> assertThrows(InvalidAvailabilityTimeException.class,
                        () -> service.create(readerUser.getId(), yeuCau(THU_HAI, "12:00", "09:00"))),
                // Bằng nhau cũng không được: một khung dài 0 phút sinh ra đúng
                // không khung trống nào, và Reader không hiểu vì sao.
                () -> assertThrows(InvalidAvailabilityTimeException.class,
                        () -> service.create(readerUser.getId(), yeuCau(THU_HAI, "09:00", "09:00"))));
        verify(availabilityRepository, never()).save(any());
    }

    @Test
    @DisplayName("Khung CHỒNG LẤN khung đã có thì bị chặn")
    void khungChongLan() {
        when(availabilityRepository.findByReaderId(reader.getId()))
                .thenReturn(List.of(khung(THU_HAI, "09:00", "12:00", true)));

        // Hai khung chồng nhau sinh ra khung trống trùng nhau ở trang đặt lịch,
        // và hai khách đặt trúng cùng một giờ của cùng một Reader.
        assertThrows(AvailabilityConflictException.class,
                () -> service.create(readerUser.getId(), yeuCau(THU_HAI, "11:00", "14:00")));
    }

    @Test
    @DisplayName("Khung nằm TRỌN trong khung đã có cũng là chồng lấn")
    void khungNamTron() {
        when(availabilityRepository.findByReaderId(reader.getId()))
                .thenReturn(List.of(khung(THU_HAI, "09:00", "17:00", true)));

        assertThrows(AvailabilityConflictException.class,
                () -> service.create(readerUser.getId(), yeuCau(THU_HAI, "10:00", "11:00")));
    }

    @Test
    @DisplayName("Khung nối ĐUÔI nhau thì KHÔNG phải chồng lấn")
    void khungNoiDuoi() {
        when(availabilityRepository.findByReaderId(reader.getId()))
                .thenReturn(List.of(khung(THU_HAI, "09:00", "12:00", true)));

        // 09:00-12:00 rồi 12:00-15:00 là hai ca liền nhau, chuyện bình thường.
        // Chặn cả trường hợp này là bắt Reader phải chừa một phút vô nghĩa.
        service.create(readerUser.getId(), yeuCau(THU_HAI, "12:00", "15:00"));
        verify(availabilityRepository).save(any());
    }

    @Test
    @DisplayName("Chồng lấn ở NGÀY KHÁC thì không tính")
    void chongLanNgayKhac() {
        when(availabilityRepository.findByReaderId(reader.getId()))
                .thenReturn(List.of(khung(THU_HAI, "09:00", "12:00", true)));

        service.create(readerUser.getId(), yeuCau((short) 2, "09:00", "12:00"));
        verify(availabilityRepository).save(any());
    }

    @Test
    @DisplayName("Khung đã TẮT không chặn khung mới")
    void khungDaTatKhongChan() {
        when(availabilityRepository.findByReaderId(reader.getId()))
                .thenReturn(List.of(khung(THU_HAI, "09:00", "12:00", false)));

        // Tắt rồi mà vẫn chặn thì Reader không bao giờ khai lại được khung cũ.
        service.create(readerUser.getId(), yeuCau(THU_HAI, "09:00", "12:00"));
        verify(availabilityRepository).save(any());
    }

    @Test
    @DisplayName("Chưa có hồ sơ Reader thì không khai lịch được")
    void chuaCoHoSo() {
        UUID la = UUID.randomUUID();
        when(readerProfileRepository.findByUserId(la)).thenReturn(Optional.empty());

        assertAll(
                () -> assertThrows(ReaderNotVerifiedException.class,
                        () -> service.create(la, yeuCau(THU_HAI, "09:00", "12:00"))),
                () -> assertThrows(ReaderNotVerifiedException.class,
                        () -> service.getWeeklySchedule(la)));
    }

    @Test
    @DisplayName("Lịch tuần trả về sắp theo thứ trong tuần")
    void lichTuanSapTheoThu() {
        when(availabilityRepository.findByReaderId(reader.getId())).thenReturn(List.of(
                khung((short) 4, "09:00", "12:00", true),
                khung((short) 0, "14:00", "17:00", true),
                khung((short) 2, "09:00", "12:00", true)));

        var lich = service.getWeeklySchedule(readerUser.getId()).getData();

        // Không sắp thì bảng lịch tuần hiện thứ Năm trên Chủ nhật.
        assertAll(
                () -> assertEquals(3, lich.size()),
                () -> assertEquals((short) 0, lich.get(0).getDayOfWeek()),
                () -> assertEquals((short) 2, lich.get(1).getDayOfWeek()),
                () -> assertEquals((short) 4, lich.get(2).getDayOfWeek()));
    }

    @Test
    @DisplayName("Lịch tuần mang theo cờ bật/tắt của từng khung")
    void lichTuanMangCoBatTat() {
        when(availabilityRepository.findByReaderId(reader.getId()))
                .thenReturn(List.of(khung(THU_HAI, "09:00", "12:00", false)));

        assertFalse(service.getWeeklySchedule(readerUser.getId()).getData().get(0).getIsActive());
    }

    @Test
    @DisplayName("Xoá khung là TẮT, không xoá dòng")
    void xoaLaTat() {
        ReaderAvailability k = khung(THU_HAI, "09:00", "12:00", true);
        when(availabilityRepository.findById(k.getId())).thenReturn(Optional.of(k));

        service.delete(readerUser.getId(), k.getId());

        // Khung giờ đã có lịch hẹn gắn vào; xoá thật là làm mồ côi những lịch ấy.
        assertFalse(k.getActive());
        verify(availabilityRepository, never()).delete(any());
        verify(availabilityRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Không xoá được khung giờ của Reader khác")
    void xoaKhungCuaReaderKhac() {
        ReaderProfile readerKhac = ReaderProfile.builder().id(UUID.randomUUID()).build();
        ReaderAvailability k = ReaderAvailability.builder()
                .id(UUID.randomUUID()).reader(readerKhac)
                .dayOfWeek(THU_HAI).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0))
                .active(true).build();
        when(availabilityRepository.findById(k.getId())).thenReturn(Optional.of(k));

        assertThrows(RuntimeException.class, () -> service.delete(readerUser.getId(), k.getId()));
        assertTrue(k.getActive());
    }

    @Test
    @DisplayName("Khung giờ không tồn tại thì báo lỗi")
    void khungKhongTonTai() {
        UUID la = UUID.randomUUID();
        when(availabilityRepository.findById(la)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.delete(readerUser.getId(), la));
    }
}
