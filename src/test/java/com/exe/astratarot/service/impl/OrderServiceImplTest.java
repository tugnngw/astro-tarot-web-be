package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.shop.CheckoutRequest;
import com.exe.astratarot.domain.entity.CartItem;
import com.exe.astratarot.domain.entity.Order;
import com.exe.astratarot.domain.entity.OrderItem;
import com.exe.astratarot.domain.entity.Product;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.OrderStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.CartItemRepository;
import com.exe.astratarot.repository.OrderRepository;
import com.exe.astratarot.repository.ProductRepository;
import com.exe.astratarot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đơn hàng. Lớp này trước đây phủ 1,0% — gần như chưa dòng nào được kiểm, mà
 * đây là nơi tồn kho bị trừ đi và tiền được chốt.
 *
 * <p>Ba bất biến:
 *
 * <ol>
 *   <li><b>Kiểm lại tồn kho ở phút chót.</b> Giỏ có thể đã nằm đó nhiều ngày;
 *       cái đã đủ lúc thêm vào giỏ có thể đã hết lúc bấm đặt.
 *   <li><b>Tồn kho trừ đi đúng một lần và trả lại đủ khi huỷ.</b> Lệch chiều ở
 *       đây là bán mất món không còn, hoặc giữ mãi một món đã trả.
 *   <li><b>Giá được đóng băng vào dòng đơn.</b> Đơn giữ tên và giá của lúc đặt,
 *       không đọc lại từ sản phẩm — shop đổi giá không được đổi số tiền một đơn
 *       đã chốt.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

    private static final long PHI_GIAO = 30_000L;

    @Mock private OrderRepository orderRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    private OrderServiceImpl service;

    private User khach;
    private Product sanPham;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, cartItemRepository,
                productRepository, userRepository);

        khach = new User();
        khach.setId(UUID.randomUUID());
        khach.setFullName("Khách");

        sanPham = Product.builder()
                .id(UUID.randomUUID())
                .name("Bộ Tarot Rider-Waite")
                .slug("bo-tarot-rider-waite")
                .price(350_000L)
                .stock(5)
                .imageUrl("https://cdn/rw.jpg")
                .active(true)
                .build();

        lenient().when(userRepository.getReferenceById(khach.getId())).thenReturn(khach);
        lenient().when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(UUID.randomUUID());
            }
            return o;
        });
    }

    private CheckoutRequest thongTinGiao() {
        CheckoutRequest r = new CheckoutRequest();
        r.setReceiverName("  Trần Duy Đạt  ");
        r.setReceiverPhone("  0900000000  ");
        r.setShippingAddress("  Số 1 Đại Cồ Việt, Hà Nội  ");
        r.setNote("Gọi trước khi giao");
        return r;
    }

    private void gioCo(Product p, int soLuong) {
        when(cartItemRepository.findAllByUserId(khach.getId())).thenReturn(List.of(
                CartItem.builder().id(UUID.randomUUID()).user(khach).product(p).quantity(soLuong).build()));
    }

    private Order don(OrderStatus tt, Product p, int soLuong) {
        Order o = Order.builder()
                .id(UUID.randomUUID())
                .user(khach)
                .orderCode("AT234567")
                .status(tt)
                .paymentStatus(PaymentStatus.UNPAID)
                .subtotal(350_000L * soLuong)
                .shippingFee(PHI_GIAO)
                .totalAmount(350_000L * soLuong + PHI_GIAO)
                .receiverName("Khách")
                .receiverPhone("0900000000")
                .shippingAddress("Hà Nội")
                .items(new ArrayList<>())
                .build();
        o.getItems().add(OrderItem.builder()
                .id(UUID.randomUUID())
                .order(o)
                .product(p)
                .productName(p == null ? "Sản phẩm đã xoá" : p.getName())
                .unitPrice(350_000L)
                .quantity(soLuong)
                .lineTotal(350_000L * soLuong)
                .build());
        lenient().when(orderRepository.findByIdAndUserIdWithItems(o.getId(), khach.getId()))
                .thenReturn(Optional.of(o));
        return o;
    }

    // =====================================================================
    // Đặt đơn
    // =====================================================================

    @Test
    @DisplayName("Đặt đơn: cộng phí giao, trừ tồn kho, dọn giỏ")
    void datDon() {
        gioCo(sanPham, 2);

        var kq = service.checkout(khach.getId(), thongTinGiao());

        assertAll(
                () -> assertEquals(700_000L, kq.getSubtotal()),
                () -> assertEquals(PHI_GIAO, kq.getShippingFee()),
                () -> assertEquals(730_000L, kq.getTotalAmount()),
                () -> assertEquals(OrderStatus.PENDING.name(), kq.getStatus()),
                () -> assertEquals(PaymentStatus.UNPAID.name(), kq.getPaymentStatus()),
                () -> assertEquals(1, kq.getItems().size()),
                () -> assertEquals(2, kq.getItems().get(0).getQuantity()),
                // Trim: địa chỉ có dấu cách hai đầu sẽ đi vào nhãn giao hàng.
                () -> assertEquals("Trần Duy Đạt", kq.getReceiverName()),
                () -> assertEquals("0900000000", kq.getReceiverPhone()),
                () -> assertEquals("Số 1 Đại Cồ Việt, Hà Nội", kq.getShippingAddress()),
                // 5 - 2 = 3.
                () -> assertEquals(3, sanPham.getStock()));
        verify(cartItemRepository).deleteAllByUserId(khach.getId());
    }

    @Test
    @DisplayName("Mã đơn: 8 ký tự, mở đầu AT, không có ký tự dễ đọc nhầm")
    void maDon() {
        gioCo(sanPham, 1);

        String ma = service.checkout(khach.getId(), thongTinGiao()).getOrderCode();

        // Mã này được đọc qua điện thoại. I/O/0/1 lẫn nhau là một đơn tra không
        // ra và một cuộc gọi lại.
        assertAll(
                () -> assertEquals(8, ma.length()),
                () -> assertTrue(ma.startsWith("AT")),
                () -> assertFalse(ma.substring(2).matches(".*[IO01].*"), "mã chứa ký tự dễ nhầm: " + ma));
    }

    @Test
    @DisplayName("Mã đơn trùng thì thử lại chứ không ném lỗi ngay")
    void maDonTrung() {
        gioCo(sanPham, 1);
        // Hai lần đầu trùng, lần thứ ba mới rỗng.
        when(orderRepository.existsByOrderCode(anyString()))
                .thenReturn(true, true, false);

        assertEquals(8, service.checkout(khach.getId(), thongTinGiao()).getOrderCode().length());
        verify(orderRepository, times(3)).existsByOrderCode(anyString());
    }

    @Test
    @DisplayName("Không sinh được mã duy nhất thì báo lỗi rõ, không ghi đơn")
    void khongSinhDuocMa() {
        gioCo(sanPham, 1);
        when(orderRepository.existsByOrderCode(anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.checkout(khach.getId(), thongTinGiao()));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Giỏ trống thì không đặt đơn")
    void gioTrong() {
        when(cartItemRepository.findAllByUserId(khach.getId())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(khach.getId(), thongTinGiao()));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hàng đã hết từ lúc bỏ vào giỏ thì chặn ở phút chót")
    void hangDaHet() {
        sanPham.setStock(1);
        gioCo(sanPham, 3);

        // Giỏ có thể đã nằm đó nhiều ngày. Cái đủ lúc thêm vào giỏ có thể đã hết
        // lúc bấm đặt, nên phải kiểm lại ở đây chứ không tin con số cũ.
        var loi = assertThrows(IllegalArgumentException.class,
                () -> service.checkout(khach.getId(), thongTinGiao()));
        assertTrue(loi.getMessage().contains("1"));
        verify(cartItemRepository, never()).deleteAllByUserId(any());
    }

    @Test
    @DisplayName("Sản phẩm ngừng bán từ lúc bỏ vào giỏ thì chặn ở phút chót")
    void sanPhamNgungBan() {
        sanPham.setActive(false);
        gioCo(sanPham, 1);

        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(khach.getId(), thongTinGiao()));
        // Tồn kho không được trừ khi đơn không thành.
        assertEquals(5, sanPham.getStock());
    }

    @Test
    @DisplayName("Giá và tên được ĐÓNG BĂNG vào dòng đơn")
    void giaDuocDongBang() {
        gioCo(sanPham, 2);

        var kq = service.checkout(khach.getId(), thongTinGiao());
        // Shop đổi giá sau khi đơn đã chốt.
        sanPham.setPrice(500_000L);
        sanPham.setName("Tên mới");

        // Dòng đơn giữ giá và tên của lúc đặt. Đọc lại từ sản phẩm thì một lần
        // đổi giá sẽ viết lại số tiền của mọi đơn cũ.
        assertAll(
                () -> assertEquals(350_000L, kq.getItems().get(0).getUnitPrice()),
                () -> assertEquals("Bộ Tarot Rider-Waite", kq.getItems().get(0).getProductName()),
                () -> assertEquals(700_000L, kq.getSubtotal()));
    }

    @Test
    @DisplayName("Nhiều dòng: tổng cộng đúng và mỗi sản phẩm trừ tồn riêng")
    void nhieuDong() {
        Product sp2 = Product.builder()
                .id(UUID.randomUUID()).name("Khăn trải bài").slug("khan")
                .price(120_000L).stock(10).active(true).build();
        when(cartItemRepository.findAllByUserId(khach.getId())).thenReturn(List.of(
                CartItem.builder().id(UUID.randomUUID()).user(khach).product(sanPham).quantity(2).build(),
                CartItem.builder().id(UUID.randomUUID()).user(khach).product(sp2).quantity(3).build()));

        var kq = service.checkout(khach.getId(), thongTinGiao());

        assertAll(
                () -> assertEquals(700_000L + 360_000L, kq.getSubtotal()),
                () -> assertEquals(700_000L + 360_000L + PHI_GIAO, kq.getTotalAmount()),
                () -> assertEquals(3, sanPham.getStock()),
                () -> assertEquals(7, sp2.getStock()));
    }

    // =====================================================================
    // Đọc
    // =====================================================================

    @Test
    @DisplayName("Danh sách đơn của mình")
    void danhSachDon() {
        // Dung don TRUOC khi stub: don() tu stub ben trong no, va Mockito khong
        // cho bat dau mot stubbing khi mot stubbing khac chua ket thuc.
        Order o = don(OrderStatus.PENDING, sanPham, 1);
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(khach.getId()))
                .thenReturn(List.of(o));

        assertEquals(1, service.getMyOrders(khach.getId()).size());
    }

    @Test
    @DisplayName("Đơn của người khác thì coi như không tồn tại")
    void donCuaNguoiKhac() {
        Order o = don(OrderStatus.PENDING, sanPham, 1);
        UUID nguoiLa = UUID.randomUUID();
        when(orderRepository.findByIdAndUserIdWithItems(o.getId(), nguoiLa))
                .thenReturn(Optional.empty());

        // Truy vấn đã ghép cả userId, nên "không thấy" chính là hàng rào quyền.
        assertAll(
                () -> assertThrows(ResourceNotFoundException.class,
                        () -> service.getMyOrder(nguoiLa, o.getId())),
                () -> assertThrows(ResourceNotFoundException.class,
                        () -> service.cancel(nguoiLa, o.getId(), "lý do")));
    }

    @Test
    @DisplayName("Xem chi tiết một đơn")
    void xemChiTiet() {
        Order o = don(OrderStatus.CONFIRMED, sanPham, 2);

        var kq = service.getMyOrder(khach.getId(), o.getId());

        assertAll(
                () -> assertEquals("AT234567", kq.getOrderCode()),
                () -> assertEquals(1, kq.getItems().size()),
                () -> assertEquals(sanPham.getId(), kq.getItems().get(0).getProductId()));
    }

    // =====================================================================
    // Huỷ đơn
    // =====================================================================

    @Test
    @DisplayName("Huỷ đơn chờ xử lý: TRẢ LẠI tồn kho")
    void huyDonChoXuLy() {
        Order o = don(OrderStatus.PENDING, sanPham, 2);
        sanPham.setStock(3);

        var kq = service.cancel(khach.getId(), o.getId(), "Đổi ý");

        // Không trả tồn thì hai cái ấy mất khỏi kho mãi mãi dù chưa bán được.
        assertAll(
                () -> assertEquals(OrderStatus.CANCELLED.name(), kq.getStatus()),
                () -> assertEquals("Đổi ý", kq.getCancelReason()),
                () -> assertEquals(5, sanPham.getStock()));
    }

    @Test
    @DisplayName("Huỷ đơn đã xác nhận cũng được, vì shop chưa gửi hàng")
    void huyDonDaXacNhan() {
        Order o = don(OrderStatus.CONFIRMED, sanPham, 1);

        assertEquals(OrderStatus.CANCELLED.name(),
                service.cancel(khach.getId(), o.getId(), null).getStatus());
    }

    @Test
    @DisplayName("Đơn đã gửi đi thì không huỷ được, và tồn kho không đổi")
    void donDaGui() {
        Order o = don(OrderStatus.SHIPPING, sanPham, 2);
        sanPham.setStock(3);

        var loi = assertThrows(IllegalArgumentException.class,
                () -> service.cancel(khach.getId(), o.getId(), "đổi ý"));
        assertAll(
                () -> assertTrue(loi.getMessage().contains("SHIPPING")),
                // Hàng đang trên đường: trả tồn ở đây là đếm hai lần một món.
                () -> assertEquals(3, sanPham.getStock()));
    }

    @Test
    @DisplayName("Huỷ hai lần thì bị chặn — không trả tồn kho hai lần")
    void huyHaiLan() {
        Order o = don(OrderStatus.CANCELLED, sanPham, 2);
        sanPham.setStock(5);

        assertThrows(IllegalArgumentException.class,
                () -> service.cancel(khach.getId(), o.getId(), "lại huỷ"));
        assertEquals(5, sanPham.getStock());
    }

    @Test
    @DisplayName("Sản phẩm đã bị xoá khỏi catalog thì huỷ đơn vẫn chạy, không nổ")
    void sanPhamDaXoa() {
        Order o = don(OrderStatus.PENDING, null, 2);

        var kq = service.cancel(khach.getId(), o.getId(), "hết hàng");

        // Không có gì để trả tồn, nhưng đơn vẫn phải huỷ được — nếu không thì
        // khách mắc kẹt với một đơn không thể huỷ vì lý do của shop.
        assertAll(
                () -> assertEquals(OrderStatus.CANCELLED.name(), kq.getStatus()),
                () -> assertEquals("Sản phẩm đã xoá", kq.getItems().get(0).getProductName()),
                () -> org.junit.jupiter.api.Assertions.assertNull(
                        kq.getItems().get(0).getProductId()));
        verify(productRepository, never()).save(any());
    }
}
