package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.support.CreateTicketRequest;
import com.exe.astratarot.domain.dto.support.ReplyRequest;
import com.exe.astratarot.domain.entity.SupportTicket;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.TicketStatus;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.SupportTicketMessageRepository;
import com.exe.astratarot.repository.SupportTicketRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hỗ trợ khách hàng. Lớp này trước đây có độ phủ 0,0% — chưa một dòng nào
 * được kiểm.
 *
 * <p>Thứ quan trọng nhất ở đây là <b>quyền xem</b>: phiếu hỗ trợ chứa chuyện
 * riêng của khách, và lọt sang người khác là rò dữ liệu cá nhân, không phải
 * lỗi hiển thị.
 *
 * <p>Thứ quan trọng thứ hai là <b>luồng trạng thái</b>. Nó quyết định phiếu
 * có nằm trong hàng chờ nhân viên hay không — sai chiều là khách nhắn vào
 * khoảng không và không ai biết.
 */
@ExtendWith(MockitoExtension.class)
class SupportServiceImplTest {

    @Mock private SupportTicketRepository ticketRepository;
    @Mock private SupportTicketMessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private SupportServiceImpl service;

    private User khach;
    private User nhanVien;
    private final UUID ticketId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SupportServiceImpl(
                ticketRepository, messageRepository, userRepository,
                notificationService);

        khach = new User();
        khach.setId(UUID.randomUUID());
        khach.setFullName("Khách");
        khach.setRole(UserRole.USER);

        nhanVien = new User();
        nhanVien.setId(UUID.randomUUID());
        nhanVien.setFullName("Nhân viên");
        nhanVien.setRole(UserRole.STAFF);

        lenient().when(userRepository.findById(khach.getId()))
                .thenReturn(Optional.of(khach));
        lenient().when(userRepository.findById(nhanVien.getId()))
                .thenReturn(Optional.of(nhanVien));
        lenient().when(ticketRepository.save(any(SupportTicket.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private SupportTicket phieu(TicketStatus tt, User nguoiNhan) {
        SupportTicket t = SupportTicket.builder()
                .id(ticketId)
                .user(khach)
                .subject("Không nhận được mail")
                .status(tt)
                .assignedTo(nguoiNhan)
                .build();
        lenient().when(ticketRepository.findById(ticketId))
                .thenReturn(Optional.of(t));
        return t;
    }

    // =====================================================================

    @Test
    @DisplayName("Tạo phiếu: trạng thái OPEN và tin đầu tiên là nội dung khách gõ")
    void taoPhieu() {
        var kq = service.createTicket(khach.getId(),
                new CreateTicketRequest("  Không nhận được mail  ", "  Chi tiết  "));

        assertAll(
                () -> assertEquals(TicketStatus.OPEN, kq.status()),
                () -> assertEquals("Không nhận được mail", kq.subject()));
        verify(ticketRepository).save(any(SupportTicket.class));
    }

    @Test
    @DisplayName("Tài khoản không tồn tại thì không tạo được phiếu")
    void taiKhoanKhongTonTai() {
        UUID la = UUID.randomUUID();
        when(userRepository.findById(la)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createTicket(la, new CreateTicketRequest("a", "b")));
    }

    @Test
    @DisplayName("Khách KHÔNG xem được phiếu của người khác")
    void khachKhongXemDuocPhieuNguoiKhac() {
        phieu(TicketStatus.OPEN, null);
        UUID nguoiLa = UUID.randomUUID();

        // Phiếu hỗ trợ chứa chuyện riêng của khách. Lọt sang người khác là rò
        // dữ liệu cá nhân, không phải lỗi hiển thị.
        assertThrows(AccessDeniedException.class,
                () -> service.getDetail(nguoiLa, false, ticketId));
    }

    @Test
    @DisplayName("Nhân viên xem được mọi phiếu")
    void nhanVienXemDuocMoiPhieu() {
        phieu(TicketStatus.OPEN, null);

        var kq = service.getDetail(nhanVien.getId(), true, ticketId);
        assertEquals(ticketId, kq.id());
    }

    @Test
    @DisplayName("Phiếu đã đóng thì không trả lời thêm được")
    void phieuDaDongThiKhongTraLoi() {
        phieu(TicketStatus.CLOSED, nhanVien);

        assertThrows(IllegalArgumentException.class,
                () -> service.reply(khach.getId(), false, ticketId,
                        new ReplyRequest("thêm")));
    }

    @Test
    @DisplayName("Nhân viên trả lời: tự nhận phiếu, chuyển sang chờ khách, báo khách")
    void nhanVienTraLoi() {
        SupportTicket t = phieu(TicketStatus.OPEN, null);

        service.reply(nhanVien.getId(), true, ticketId, new ReplyRequest("  Đã xử  "));

        assertAll(
                // Chưa ai nhận thì người trả lời đầu tiên nhận luôn — nếu
                // không, phiếu nằm mãi trong hàng chờ dù đã có người xử.
                () -> assertEquals(nhanVien, t.getAssignedTo()),
                () -> assertEquals(TicketStatus.PENDING, t.getStatus()),
                () -> verify(notificationService).push(eq(khach), anyString(),
                        anyString(), anyString(), any()));
    }

    @Test
    @DisplayName("Khách trả lời thì phiếu quay LẠI hàng chờ nhân viên")
    void khachTraLoiThiVeHangCho() {
        SupportTicket t = phieu(TicketStatus.PENDING, nhanVien);

        service.reply(khach.getId(), false, ticketId, new ReplyRequest("vẫn chưa được"));

        // Không kéo về OPEN thì phiếu kẹt ở "chờ khách" mãi, và khách nhắn vào
        // khoảng không vì không ai nhìn hàng chờ nữa.
        assertAll(
                () -> assertEquals(TicketStatus.OPEN, t.getStatus()),
                () -> verify(notificationService).push(eq(nhanVien), anyString(),
                        anyString(), anyString(), any()));
    }

    @Test
    @DisplayName("Khách trả lời phiếu ĐÃ XONG thì không kéo ngược trạng thái")
    void khachTraLoiPhieuDaXong() {
        SupportTicket t = phieu(TicketStatus.RESOLVED, nhanVien);

        service.reply(khach.getId(), false, ticketId, new ReplyRequest("cảm ơn"));

        // Một lời cảm ơn không nên mở lại phiếu đã giải quyết.
        assertEquals(TicketStatus.RESOLVED, t.getStatus());
    }

    @Test
    @DisplayName("Khách trả lời khi chưa ai nhận thì báo cho TẤT CẢ nhân sự")
    void chuaAiNhanThiBaoTatCa() {
        phieu(TicketStatus.OPEN, null);
        User ns1 = new User();
        ns1.setId(UUID.randomUUID());
        User ns2 = new User();
        ns2.setId(UUID.randomUUID());
        when(userRepository.findByRoleInAndDeletedAtIsNull(
                List.of(UserRole.STAFF, UserRole.MANAGER, UserRole.ADMIN)))
                .thenReturn(List.of(ns1, ns2));

        service.reply(khach.getId(), false, ticketId, new ReplyRequest("alo"));

        // Chưa ai nhận mà chỉ báo cho một người thì rất dễ không tới ai cả.
        verify(notificationService, times(2))
                .push(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Nhân viên trả lời phiếu CỦA CHÍNH MÌNH thì tính là khách")
    void nhanVienTraLoiPhieuCuaChinhMinh() {
        // Nhân viên cũng là người dùng và cũng gửi yêu cầu hỗ trợ được. Lúc đó
        // họ là KHÁCH của phiếu ấy — coi là nhân viên thì phiếu tự nhận chính
        // mình rồi chuyển sang "chờ khách", tức là chờ chính họ.
        SupportTicket t = SupportTicket.builder()
                .id(ticketId)
                .user(nhanVien)
                .subject("Tôi cần giúp")
                .status(TicketStatus.OPEN)
                .build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(t));

        service.reply(nhanVien.getId(), true, ticketId, new ReplyRequest("bổ sung"));

        assertAll(
                () -> assertNull(t.getAssignedTo()),
                () -> assertEquals(TicketStatus.OPEN, t.getStatus()));
    }

    @Test
    @DisplayName("Phiếu không tồn tại thì báo đúng loại lỗi")
    void phieuKhongTonTai() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getDetail(khach.getId(), false, ticketId));
    }

    @Test
    @DisplayName("Nhân viên đổi trạng thái phiếu")
    void doiTrangThai() {
        SupportTicket t = phieu(TicketStatus.PENDING, nhanVien);

        service.updateStatus(nhanVien.getId(), ticketId, TicketStatus.RESOLVED);

        assertEquals(TicketStatus.RESOLVED, t.getStatus());
        verify(notificationService, never().description("đổi trạng thái không "
                + "phải lúc nào cũng cần báo khách"))
                .push(any(), anyString(), anyString(), anyString(), any());
    }
}
