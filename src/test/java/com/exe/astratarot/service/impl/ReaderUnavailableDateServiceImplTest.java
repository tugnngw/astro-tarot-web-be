package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.reader.AddUnavailableDateRequest;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.ReaderUnavailableDate;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.ReaderNotVerifiedException;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.ReaderUnavailableDateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ngày Reader báo bận. Lớp này trước đây phủ 0,0%.
 *
 * <p>Nhỏ nhưng quan trọng: đây là thứ duy nhất cho Reader nghỉ một ngày cụ thể
 * mà không phải xoá cả lịch làm việc hằng tuần. Nó thắng lịch tuần ở
 * {@code BookingServiceImpl.availableSlots}, nên sai ở đây là Reader nhận lịch
 * vào đúng ngày họ đã báo bận.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReaderUnavailableDateServiceImplTest {

    @Mock private ReaderUnavailableDateRepository unavailableDateRepository;
    @Mock private ReaderProfileRepository readerProfileRepository;

    private ReaderUnavailableDateServiceImpl service;

    private User readerUser;
    private ReaderProfile reader;
    private final LocalDate ngayNghi = LocalDate.now().plusDays(10);

    @BeforeEach
    void setUp() {
        service = new ReaderUnavailableDateServiceImpl(unavailableDateRepository,
                readerProfileRepository);

        readerUser = new User();
        readerUser.setId(UUID.randomUUID());

        reader = ReaderProfile.builder().id(UUID.randomUUID()).user(readerUser).build();

        lenient().when(readerProfileRepository.findByUserId(readerUser.getId()))
                .thenReturn(Optional.of(reader));
        lenient().when(unavailableDateRepository.existsByReaderIdAndUnavailableDate(any(), any()))
                .thenReturn(false);
        lenient().when(unavailableDateRepository.findByReaderId(reader.getId()))
                .thenReturn(List.of());
        lenient().when(unavailableDateRepository.save(any(ReaderUnavailableDate.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private AddUnavailableDateRequest yeuCau(LocalDate ngay, String lyDo) {
        AddUnavailableDateRequest r = new AddUnavailableDateRequest();
        r.setUnavailableDate(ngay);
        r.setReason(lyDo);
        return r;
    }

    // =====================================================================

    @Test
    @DisplayName("Báo bận một ngày: gắn đúng Reader và giữ lý do")
    void baoBanMotNgay() {
        service.addDate(readerUser.getId(), yeuCau(ngayNghi, "Đi khám bệnh"));

        ArgumentCaptor<ReaderUnavailableDate> bat =
                ArgumentCaptor.forClass(ReaderUnavailableDate.class);
        verify(unavailableDateRepository).save(bat.capture());
        assertAll(
                () -> assertEquals(reader, bat.getValue().getReader()),
                () -> assertEquals(ngayNghi, bat.getValue().getUnavailableDate()),
                () -> assertEquals("Đi khám bệnh", bat.getValue().getReason()));
    }

    @Test
    @DisplayName("Không nêu lý do cũng báo bận được")
    void khongNeuLyDo() {
        service.addDate(readerUser.getId(), yeuCau(ngayNghi, null));

        // Bắt buộc nêu lý do là hỏi một câu riêng tư cho một việc không cần
        // riêng tư. Reader nghỉ thì nghỉ.
        verify(unavailableDateRepository).save(any());
    }

    @Test
    @DisplayName("Báo bận TRÙNG ngày thì bị chặn")
    void baoBanTrungNgay() {
        when(unavailableDateRepository.existsByReaderIdAndUnavailableDate(reader.getId(), ngayNghi))
                .thenReturn(true);

        // Bảng có ràng buộc duy nhất theo (reader, ngày); để lọt thì lỗi hiện
        // ra là một DataIntegrityViolation khó đọc thay vì một câu rõ ràng.
        assertThrows(RuntimeException.class,
                () -> service.addDate(readerUser.getId(), yeuCau(ngayNghi, "lần hai")));
        verify(unavailableDateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chưa có hồ sơ Reader thì không báo bận được")
    void chuaCoHoSo() {
        UUID la = UUID.randomUUID();
        when(readerProfileRepository.findByUserId(la)).thenReturn(Optional.empty());

        assertAll(
                () -> assertThrows(ReaderNotVerifiedException.class,
                        () -> service.addDate(la, yeuCau(ngayNghi, null))),
                () -> assertThrows(ReaderNotVerifiedException.class,
                        () -> service.getUnavailableDates(la)));
    }

    @Test
    @DisplayName("Danh sách ngày bận mang đủ ngày và lý do")
    void danhSachNgayBan() {
        when(unavailableDateRepository.findByReaderId(reader.getId())).thenReturn(List.of(
                ReaderUnavailableDate.builder().id(UUID.randomUUID()).reader(reader)
                        .unavailableDate(ngayNghi).reason("Đi khám bệnh").build(),
                ReaderUnavailableDate.builder().id(UUID.randomUUID()).reader(reader)
                        .unavailableDate(ngayNghi.plusDays(1)).build()));

        var ds = service.getUnavailableDates(readerUser.getId()).getData();

        assertAll(
                () -> assertEquals(2, ds.size()),
                () -> assertEquals(ngayNghi, ds.get(0).getUnavailableDate()),
                () -> assertEquals("Đi khám bệnh", ds.get(0).getReason()));
    }

    @Test
    @DisplayName("Chưa báo bận ngày nào thì trả danh sách rỗng, không null")
    void chuaBaoBanNgayNao() {
        assertTrue(service.getUnavailableDates(readerUser.getId()).getData().isEmpty());
    }

    @Test
    @DisplayName("Gỡ ngày bận thì xoá hẳn dòng")
    void goNgayBan() {
        ReaderUnavailableDate ngay = ReaderUnavailableDate.builder()
                .id(UUID.randomUUID()).reader(reader).unavailableDate(ngayNghi).build();
        when(unavailableDateRepository.findById(ngay.getId())).thenReturn(Optional.of(ngay));

        service.removeDate(readerUser.getId(), ngay.getId());

        // Khác với khung giờ tuần: ngày bận không có lịch hẹn nào gắn vào nên
        // xoá hẳn được, và giữ lại một ngày bận đã tắt chỉ làm rối bảng.
        verify(unavailableDateRepository).delete(ngay);
    }

    @Test
    @DisplayName("Không gỡ được ngày bận của Reader khác")
    void goNgayCuaReaderKhac() {
        ReaderProfile readerKhac = ReaderProfile.builder().id(UUID.randomUUID()).build();
        ReaderUnavailableDate ngay = ReaderUnavailableDate.builder()
                .id(UUID.randomUUID()).reader(readerKhac).unavailableDate(ngayNghi).build();
        when(unavailableDateRepository.findById(ngay.getId())).thenReturn(Optional.of(ngay));

        // Gỡ ngày bận của người khác là ép họ nhận lịch vào ngày họ đã xin nghỉ.
        assertThrows(RuntimeException.class,
                () -> service.removeDate(readerUser.getId(), ngay.getId()));
        verify(unavailableDateRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Ngày bận không tồn tại thì báo lỗi")
    void ngayKhongTonTai() {
        UUID la = UUID.randomUUID();
        when(unavailableDateRepository.findById(la)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.removeDate(readerUser.getId(), la));
    }
}
