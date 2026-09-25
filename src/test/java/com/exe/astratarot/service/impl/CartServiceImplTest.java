package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.shop.AddToCartRequest;
import com.exe.astratarot.domain.dto.shop.UpdateCartItemRequest;
import com.exe.astratarot.domain.entity.CartItem;
import com.exe.astratarot.domain.entity.Product;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.CartItemRepository;
import com.exe.astratarot.repository.ProductRepository;
import com.exe.astratarot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
 * Giỏ hàng. Lớp này trước đây phủ 0,0%.
 *
 * <p>Hai chỗ dễ sai nhất, và cả hai đều chỉ lộ ra khi đã bán mất hàng:
 *
 * <ol>
 *   <li><b>Cộng dồn, không tạo dòng mới.</b> Bảng có UNIQUE(user_id,
 *       product_id), nên thêm lại đúng sản phẩm đã có mà tạo dòng thứ hai là
 *       một lỗi ràng buộc ném vào mặt khách ở giữa lúc mua.
 *   <li><b>Kiểm tồn kho theo số SAU khi cộng dồn.</b> Còn 5 cái, thêm 3 rồi
 *       thêm 3 nữa — nếu mỗi lần chỉ kiểm riêng 3 thì cả hai lần đều qua, và
 *       giỏ giữ 6 cái của một món chỉ còn 5.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceImplTest {

    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    private CartServiceImpl service;

    private User khach;
    private Product sanPham;

    @BeforeEach
    void setUp() {
        service = new CartServiceImpl(cartItemRepository, productRepository, userRepository);

        khach = new User();
        khach.setId(UUID.randomUUID());

        sanPham = Product.builder()
                .id(UUID.randomUUID())
                .name("Bộ Tarot Rider-Waite")
                .slug("bo-tarot-rider-waite")
                .price(350_000L)
                .stock(5)
                .imageUrl("https://cdn/rw.jpg")
                .active(true)
                .build();

        lenient().when(productRepository.findById(sanPham.getId())).thenReturn(Optional.of(sanPham));
        lenient().when(userRepository.getReferenceById(khach.getId())).thenReturn(khach);
        lenient().when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CartItem dongGio(int soLuong) {
        CartItem ci = CartItem.builder()
                .id(UUID.randomUUID())
                .user(khach)
                .product(sanPham)
                .quantity(soLuong)
                .build();
        lenient().when(cartItemRepository.findByIdAndUserId(ci.getId(), khach.getId()))
                .thenReturn(Optional.of(ci));
        lenient().when(cartItemRepository.findByUserIdAndProductId(khach.getId(), sanPham.getId()))
                .thenReturn(Optional.of(ci));
        return ci;
    }

    private AddToCartRequest themVao(int soLuong) {
        AddToCartRequest r = new AddToCartRequest();
        r.setProductId(sanPham.getId());
        r.setQuantity(soLuong);
        return r;
    }

    // =====================================================================

    @Test
    @DisplayName("Giỏ rỗng: tổng bằng 0, không âm và không null")
    void gioRong() {
        when(cartItemRepository.findAllByUserId(khach.getId())).thenReturn(List.of());

        var gio = service.getCart(khach.getId());

        assertAll(
                () -> assertTrue(gio.getItems().isEmpty()),
                () -> assertEquals(0, gio.getTotalQuantity()),
                () -> assertEquals(0L, gio.getSubtotal()));
    }

    @Test
    @DisplayName("Thêm sản phẩm mới: dựng dòng giỏ và tính đúng tiền dòng")
    void themSanPhamMoi() {
        when(cartItemRepository.findByUserIdAndProductId(khach.getId(), sanPham.getId()))
                .thenReturn(Optional.empty());
        when(cartItemRepository.findAllByUserId(khach.getId()))
                .thenReturn(List.of(CartItem.builder()
                        .id(UUID.randomUUID()).user(khach).product(sanPham).quantity(2).build()));

        var gio = service.addItem(khach.getId(), themVao(2));

        assertAll(
                () -> assertEquals(1, gio.getItems().size()),
                () -> assertEquals(2, gio.getTotalQuantity()),
                () -> assertEquals(700_000L, gio.getSubtotal()),
                () -> assertEquals(700_000L, gio.getItems().get(0).getLineTotal()),
                () -> assertEquals("bo-tarot-rider-waite", gio.getItems().get(0).getProductSlug()),
                // Tồn kho phải đi theo dòng giỏ, vì đó là con số giao diện dùng
                // để chặn nút tăng số lượng.
                () -> assertEquals(5, gio.getItems().get(0).getStock()));
    }

    @Test
    @DisplayName("Thêm lại sản phẩm đã có thì CỘNG DỒN, không tạo dòng thứ hai")
    void themLaiThiCongDon() {
        CartItem cu = dongGio(2);
        when(cartItemRepository.findAllByUserId(khach.getId())).thenReturn(List.of(cu));

        service.addItem(khach.getId(), themVao(1));

        // Bảng có UNIQUE(user_id, product_id): tạo dòng thứ hai là lỗi ràng
        // buộc ném vào mặt khách ở giữa lúc mua.
        ArgumentCaptor<CartItem> bat = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(bat.capture());
        assertAll(
                () -> assertEquals(cu.getId(), bat.getValue().getId()),
                () -> assertEquals(3, bat.getValue().getQuantity()));
    }

    @Test
    @DisplayName("Cộng dồn VƯỢT tồn kho thì bị chặn, dù mỗi lần thêm đều nhỏ hơn tồn")
    void congDonVuotTonKho() {
        dongGio(3);

        // Còn 5, trong giỏ đã 3, thêm 3 nữa là 6. Nếu chỉ kiểm riêng con số 3
        // của lần này thì cả hai lần đều qua và giỏ giữ 6 cái của món còn 5.
        var loi = assertThrows(IllegalArgumentException.class,
                () -> service.addItem(khach.getId(), themVao(3)));
        assertTrue(loi.getMessage().contains("5"));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sản phẩm đã ngừng bán thì không thêm vào giỏ được")
    void sanPhamNgungBan() {
        sanPham.setActive(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.addItem(khach.getId(), themVao(1)));
    }

    @Test
    @DisplayName("Sản phẩm không tồn tại thì báo đúng loại lỗi")
    void sanPhamKhongTonTai() {
        UUID la = UUID.randomUUID();
        when(productRepository.findById(la)).thenReturn(Optional.empty());
        AddToCartRequest r = new AddToCartRequest();
        r.setProductId(la);
        r.setQuantity(1);

        assertThrows(ResourceNotFoundException.class, () -> service.addItem(khach.getId(), r));
    }

    @Test
    @DisplayName("Sửa số lượng: ghi đè chứ không cộng thêm")
    void suaSoLuong() {
        CartItem cu = dongGio(4);
        when(cartItemRepository.findAllByUserId(khach.getId())).thenReturn(List.of(cu));
        UpdateCartItemRequest r = new UpdateCartItemRequest();
        r.setQuantity(2);

        service.updateItem(khach.getId(), cu.getId(), r);

        // Sửa là đặt lại con số, không phải cộng thêm — nhầm chỗ này thì khách
        // giảm số lượng lại thành tăng.
        assertEquals(2, cu.getQuantity());
    }

    @Test
    @DisplayName("Sửa vượt tồn kho thì bị chặn")
    void suaVuotTonKho() {
        CartItem cu = dongGio(1);
        UpdateCartItemRequest r = new UpdateCartItemRequest();
        r.setQuantity(9);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateItem(khach.getId(), cu.getId(), r));
        assertEquals(1, cu.getQuantity());
    }

    @Test
    @DisplayName("Không sửa được dòng giỏ của người khác")
    void dongGioCuaNguoiKhac() {
        CartItem cu = dongGio(1);
        UUID nguoiLa = UUID.randomUUID();
        when(cartItemRepository.findByIdAndUserId(cu.getId(), nguoiLa))
                .thenReturn(Optional.empty());
        UpdateCartItemRequest r = new UpdateCartItemRequest();
        r.setQuantity(2);

        // Truy vấn đã ghép cả userId, nên "không thấy" chính là hàng rào quyền.
        assertAll(
                () -> assertThrows(ResourceNotFoundException.class,
                        () -> service.updateItem(nguoiLa, cu.getId(), r)),
                () -> assertThrows(ResourceNotFoundException.class,
                        () -> service.removeItem(nguoiLa, cu.getId())));
    }

    @Test
    @DisplayName("Xoá một dòng giỏ")
    void xoaMotDong() {
        CartItem cu = dongGio(1);
        when(cartItemRepository.findAllByUserId(khach.getId())).thenReturn(List.of());

        var gio = service.removeItem(khach.getId(), cu.getId());

        verify(cartItemRepository).delete(cu);
        assertEquals(0, gio.getTotalQuantity());
    }

    @Test
    @DisplayName("Dọn cả giỏ")
    void donCaGio() {
        service.clear(khach.getId());

        verify(cartItemRepository).deleteAllByUserId(khach.getId());
    }

    @Test
    @DisplayName("Nhiều dòng: tổng số lượng và tổng tiền cộng đúng")
    void nhieuDong() {
        Product sp2 = Product.builder()
                .id(UUID.randomUUID()).name("Khăn trải bài").slug("khan-trai-bai")
                .price(120_000L).stock(10).active(true).build();
        when(cartItemRepository.findAllByUserId(khach.getId())).thenReturn(List.of(
                CartItem.builder().id(UUID.randomUUID()).user(khach).product(sanPham).quantity(2).build(),
                CartItem.builder().id(UUID.randomUUID()).user(khach).product(sp2).quantity(3).build()));

        var gio = service.getCart(khach.getId());

        assertAll(
                () -> assertEquals(5, gio.getTotalQuantity()),
                () -> assertEquals(700_000L + 360_000L, gio.getSubtotal()));
    }
}
