package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.reader.ReaderProfileResponse;
import com.exe.astratarot.domain.dto.reader.UpdateProfileRequest;
import com.exe.astratarot.domain.entity.ReaderApplication;
import com.exe.astratarot.domain.entity.ReaderAvailability;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.ReaderUnavailableDate;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.mapper.ReaderAvailabilityMapper;
import com.exe.astratarot.domain.mapper.ReaderProfileMapper;
import com.exe.astratarot.domain.mapper.ReaderUnavailableDateMapper;
import com.exe.astratarot.exception.ReaderNotVerifiedException;
import com.exe.astratarot.repository.ReaderApplicationRepository;
import com.exe.astratarot.repository.ReaderAvailabilityRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.ReaderUnavailableDateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hồ sơ Reader. Lớp này trước đây phủ 0,0%.
 *
 * <p>Điểm đáng kiểm nhất là một cái bẫy đã gây lỗi thật: <b>MapStruct chỉ nhìn
 * thấy {@code ReaderProfile}</b>, mà tên và ảnh của Reader nằm bên {@code User}.
 * Nên service phải gán tay ba trường ấy sau khi mapper chạy. Quên là trang công
 * khai gọi Reader bằng… không gì cả.
 *
 * <p>Cái bẫy y hệt đã xảy ra ở danh sách đơn chờ duyệt: trước đây chỉ trả
 * username, nên trang quản trị hiện "Người dùng không rõ tên" và email "—" cho
 * mọi đơn — quản trị viên duyệt hồ sơ mà không biết mình đang duyệt ai.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReaderProfileServiceImplTest {

    @Mock private ReaderProfileRepository readerProfileRepository;
    @Mock private ReaderAvailabilityRepository availabilityRepository;
    @Mock private ReaderUnavailableDateRepository unavailableDateRepository;
    @Mock private ReaderApplicationRepository applicationRepository;
    @Mock private ReaderProfileMapper readerProfileMapper;
    @Mock private ReaderAvailabilityMapper availabilityMapper;
    @Mock private ReaderUnavailableDateMapper unavailableDateMapper;

    private ReaderProfileServiceImpl service;

    private User readerUser;
    private ReaderProfile reader;

    @BeforeEach
    void setUp() {
        service = new ReaderProfileServiceImpl(readerProfileRepository, availabilityRepository,
                unavailableDateRepository, applicationRepository, readerProfileMapper,
                availabilityMapper, unavailableDateMapper);

        readerUser = new User();
        readerUser.setId(UUID.randomUUID());
        readerUser.setUsername("reader01");
        readerUser.setFullName("Lê Thu Lan");
        readerUser.setEmail("lan@example.com");
        readerUser.setAvatar("https://cdn/lan.jpg");

        reader = ReaderProfile.builder()
                .id(UUID.randomUUID())
                .user(readerUser)
                .bio("Chuyên tình cảm")
                .yearsExperience(5)
                .build();

        lenient().when(readerProfileRepository.findByUserId(readerUser.getId()))
                .thenReturn(Optional.of(reader));
        lenient().when(readerProfileRepository.findById(reader.getId()))
                .thenReturn(Optional.of(reader));
        lenient().when(availabilityRepository.findByReaderId(reader.getId())).thenReturn(List.of());
        lenient().when(unavailableDateRepository.findByReaderId(reader.getId())).thenReturn(List.of());
        // Mapper chỉ dựng phần thuộc về ReaderProfile — đúng như MapStruct sinh
        // ra. Ba trường của User cố tình để trống để phép kiểm bắt được việc
        // service có gán tay hay không.
        lenient().when(readerProfileMapper.toResponse(any(ReaderProfile.class)))
                .thenAnswer(inv -> {
                    ReaderProfile p = inv.getArgument(0);
                    return ReaderProfileResponse.builder()
                            .id(p.getId())
                            .bio(p.getBio())
                            .yearsExperience(p.getYearsExperience())
                            .build();
                });
    }

    // =====================================================================

    @Test
    @DisplayName("Tên và ảnh của Reader được GÁN TAY sau khi mapper chạy")
    void ganTayTenVaAnh() {
        var kq = service.getMyProfile(readerUser.getId()).getData();

        // MapStruct chỉ nhìn thấy ReaderProfile; tên và ảnh nằm bên User. Quên
        // gán là trang công khai gọi Reader bằng không gì cả.
        assertAll(
                () -> assertEquals(readerUser.getId(), kq.getUserId()),
                () -> assertEquals("Lê Thu Lan", kq.getFullName()),
                () -> assertEquals("https://cdn/lan.jpg", kq.getAvatar()),
                () -> assertEquals("Chuyên tình cảm", kq.getBio()));
    }

    @Test
    @DisplayName("Hồ sơ không gắn User thì vẫn trả về được, không nổ")
    void hoSoKhongGanUser() {
        reader.setUser(null);

        // Dữ liệu lệch không được làm sập cả trang danh sách Reader.
        assertEquals(reader.getId(), service.getMyProfile(readerUser.getId()).getData().getId());
    }

    @Test
    @DisplayName("Hồ sơ mang theo lịch tuần và ngày bận")
    void mangLichTuanVaNgayBan() {
        ReaderAvailability khung = ReaderAvailability.builder()
                .id(UUID.randomUUID()).reader(reader).dayOfWeek((short) 1)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).active(true).build();
        ReaderUnavailableDate ngay = ReaderUnavailableDate.builder()
                .id(UUID.randomUUID()).reader(reader)
                .unavailableDate(LocalDate.now().plusDays(3)).build();
        when(availabilityRepository.findByReaderId(reader.getId())).thenReturn(List.of(khung));
        when(unavailableDateRepository.findByReaderId(reader.getId())).thenReturn(List.of(ngay));
        when(availabilityMapper.toResponse(any())).thenReturn(
                com.exe.astratarot.domain.dto.reader.ReaderAvailabilityResponse.builder()
                        .id(khung.getId()).dayOfWeek((short) 1).build());
        when(unavailableDateMapper.toResponse(any())).thenReturn(
                com.exe.astratarot.domain.dto.reader.ReaderUnavailableDateResponse.builder()
                        .id(ngay.getId()).unavailableDate(ngay.getUnavailableDate()).build());

        var kq = service.getMyProfile(readerUser.getId()).getData();

        assertAll(
                () -> assertEquals(1, kq.getWeeklyAvailability().size()),
                () -> assertEquals(1, kq.getUnavailableDates().size()));
    }

    @Test
    @DisplayName("Chưa có hồ sơ thì mọi đường đọc và sửa đều bị chặn")
    void chuaCoHoSo() {
        UUID la = UUID.randomUUID();
        when(readerProfileRepository.findByUserId(la)).thenReturn(Optional.empty());

        assertAll(
                () -> assertThrows(ReaderNotVerifiedException.class, () -> service.getMyProfile(la)),
                () -> assertThrows(ReaderNotVerifiedException.class,
                        () -> service.updateProfile(la, new UpdateProfileRequest())));
    }

    @Test
    @DisplayName("Sửa hồ sơ đi qua mapper rồi mới lưu")
    void suaHoSo() {
        UpdateProfileRequest r = new UpdateProfileRequest();

        service.updateProfile(readerUser.getId(), r);

        // Gán tay từng trường ở service thì mỗi lần thêm trường mới lại phải
        // nhớ sửa ở hai nơi; mapper giữ cho hai nơi ấy là một.
        verify(readerProfileMapper).updateFromRequest(r, reader);
        verify(readerProfileRepository).save(reader);
    }

    @Test
    @DisplayName("Danh sách Reader công khai chỉ lấy người ĐẶT LỊCH ĐƯỢC")
    void danhSachCongKhai() {
        when(readerProfileRepository.findBookable()).thenReturn(List.of(reader));

        var ds = service.getAllVerifiedReaders().getData();

        // findBookable đã lọc sẵn: đã duyệt, đang nhận lịch, và đã đặt giá.
        // Hiện một Reader chưa đặt giá là dẫn khách tới một trang không đặt
        // được gì.
        assertAll(
                () -> assertEquals(1, ds.size()),
                () -> assertEquals("Lê Thu Lan", ds.get(0).getFullName()));
    }

    @Test
    @DisplayName("Chưa có Reader nào thì trả danh sách rỗng, không null")
    void chuaCoReaderNao() {
        when(readerProfileRepository.findBookable()).thenReturn(List.of());

        assertTrue(service.getAllVerifiedReaders().getData().isEmpty());
    }

    @Test
    @DisplayName("Xem một Reader theo id")
    void xemMotReader() {
        assertEquals("Lê Thu Lan",
                service.getReaderById(reader.getId()).getData().getFullName());
    }

    @Test
    @DisplayName("Reader không tồn tại thì báo lỗi")
    void readerKhongTonTai() {
        UUID la = UUID.randomUUID();
        when(readerProfileRepository.findById(la)).thenReturn(Optional.empty());

        assertThrows(ReaderNotVerifiedException.class, () -> service.getReaderById(la));
    }

    @Test
    @DisplayName("Đơn chờ duyệt mang đủ TÊN và EMAIL, không chỉ tên đăng nhập")
    void donChoDuyetDuTenVaEmail() {
        ReaderApplication don = ReaderApplication.builder()
                .id(UUID.randomUUID())
                .user(readerUser)
                .bio("Tôi đọc Tarot 5 năm")
                .experience(5)
                .specialties(new String[]{"Tarot", "Tình cảm"})
                .status(ReaderApplication.ApplicationStatus.PENDING)
                .build();
        when(applicationRepository.findAll()).thenReturn(List.of(don));

        Map<String, Object> hang = service.getAllPendingApplications(UUID.randomUUID())
                .getData().get(0);

        // Trước đây chỉ trả username, nên trang quản trị hiện "Người dùng không
        // rõ tên" và email "—" cho mọi đơn — quản trị viên duyệt hồ sơ mà không
        // biết mình đang duyệt ai.
        assertAll(
                () -> assertEquals("Lê Thu Lan", hang.get("fullName")),
                () -> assertEquals("lan@example.com", hang.get("email")),
                () -> assertEquals("reader01", hang.get("username")),
                () -> assertEquals(readerUser.getId(), hang.get("userId")),
                () -> assertEquals(5, hang.get("experience")),
                () -> assertEquals("PENDING", hang.get("status")));
    }

    @Test
    @DisplayName("Đơn ĐÃ xử lý không nằm trong hàng chờ")
    void donDaXuLyKhongNamTrongHangCho() {
        ReaderApplication choDuyet = ReaderApplication.builder()
                .id(UUID.randomUUID()).user(readerUser)
                .status(ReaderApplication.ApplicationStatus.PENDING).build();
        ReaderApplication daDuyet = ReaderApplication.builder()
                .id(UUID.randomUUID()).user(readerUser)
                .status(ReaderApplication.ApplicationStatus.APPROVED).build();
        ReaderApplication daTuChoi = ReaderApplication.builder()
                .id(UUID.randomUUID()).user(readerUser)
                .status(ReaderApplication.ApplicationStatus.REJECTED).build();
        when(applicationRepository.findAll()).thenReturn(List.of(choDuyet, daDuyet, daTuChoi));

        var hangCho = service.getAllPendingApplications(UUID.randomUUID()).getData();

        // Hàng chờ lẫn đơn đã xử lý thì người duyệt phải tự nhớ mình đã duyệt
        // cái nào, và sớm muộn sẽ duyệt lại một đơn.
        assertAll(
                () -> assertEquals(1, hangCho.size()),
                () -> assertEquals(choDuyet.getId(), hangCho.get(0).get("id")));
    }

    @Test
    @DisplayName("Không có đơn nào thì trả danh sách rỗng")
    void khongCoDonNao() {
        when(applicationRepository.findAll()).thenReturn(List.of());

        assertTrue(service.getAllPendingApplications(UUID.randomUUID()).getData().isEmpty());
    }
}
