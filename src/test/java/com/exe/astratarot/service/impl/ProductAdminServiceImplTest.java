package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.shop.SaveProductRequest;
import com.exe.astratarot.domain.entity.Product;
import com.exe.astratarot.domain.entity.ProductCategory;
import com.exe.astratarot.domain.entity.ProductClick;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.ProductCategoryRepository;
import com.exe.astratarot.repository.ProductClickRepository;
import com.exe.astratarot.repository.ProductRepository;
import com.exe.astratarot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Quản lý sản phẩm liên kết. Lớp này trước đây phủ 1,6%.
 *
 * <p>Shop không bán hàng trực tiếp: hàng nằm trên sàn, mình giới thiệu và ăn
 * hoa hồng. Nên hai thứ đáng kiểm không phải tồn kho, mà là:
 *
 * <ol>
 *   <li><b>Slug không đổi khi đổi tên.</b> Slug nằm trong URL. Sinh lại slug
 *       lúc sửa là làm hỏng mọi đường dẫn đã chia sẻ — và với một shop liên kết
 *       thì đường dẫn đã chia sẻ chính là kênh kiếm tiền.
 *   <li><b>Liên kết phải là http(s).</b> Chặn ở đây thay vì để khách bấm rồi
 *       mới biết: link sai giao thức mở ra trang trắng hoặc bị trình duyệt
 *       chặn, và lượt bấm ấy mất luôn.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductAdminServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductCategoryRepository categoryRepository;
    @Mock private ProductClickRepository clickRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.exe.astratarot.service.ActivityLogService activityLogService;

    private ProductAdminServiceImpl service;

    private final UUID adminId = UUID.randomUUID();
    private ProductCategory danhMuc;

    @BeforeEach
    void setUp() {
        service = new ProductAdminServiceImpl(productRepository, categoryRepository,
                clickRepository, userRepository, activityLogService);

        danhMuc = ProductCategory.builder()
                .id(UUID.randomUUID())
                .name("Bộ bài")
                .slug("bo-bai")
                .build();

        lenient().when(categoryRepository.findById(danhMuc.getId())).thenReturn(Optional.of(danhMuc));
        lenient().when(productRepository.existsBySlug(anyString())).thenReturn(false);
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
    }

    private SaveProductRequest yeuCau(String ten) {
        SaveProductRequest r = new SaveProductRequest();
        r.setName(ten);
        r.setDescription("Mô tả");
        r.setPrice(350_000L);
        r.setImageUrl("https://cdn/a.jpg");
        r.setAffiliateUrl("https://shopee.vn/product/1");
        r.setAffiliatePlatform("shopee");
        r.setCommissionPercent(new BigDecimal("5.0"));
        r.setCategoryId(danhMuc.getId());
        return r;
    }

    private Product sanPham(String ten, String slug) {
        Product p = Product.builder()
                .id(UUID.randomUUID())
                .name(ten)
                .slug(slug)
                .price(350_000L)
                .stock(5)
                .affiliateUrl("https://shopee.vn/product/1")
                .affiliatePlatform("SHOPEE")
                .commissionPercent(new BigDecimal("5.0"))
                .clickCount(10L)
                .active(true)
                .category(danhMuc)
                .build();
        lenient().when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        lenient().when(productRepository.findBySlugAndActiveTrue(slug)).thenReturn(Optional.of(p));
        return p;
    }

    // =====================================================================
    // Tạo và sửa
    // =====================================================================

    @Nested
    @DisplayName("Tạo và sửa")
    class TaoVaSua {

        @Test
        @DisplayName("Tạo: slug bỏ dấu tiếng Việt, sàn viết hoa, danh mục gắn đúng")
        void tao() {
            var kq = service.create(adminId, yeuCau("  Bộ bài Thoth Đặc Biệt  "));

            assertAll(
                    () -> assertEquals("Bộ bài Thoth Đặc Biệt", kq.getName()),
                    // Slug nằm trong URL nên phải không dấu và ổn định.
                    () -> assertEquals("bo-bai-thoth-dac-biet", kq.getSlug()),
                    () -> assertEquals("SHOPEE", kq.getAffiliatePlatform()),
                    () -> assertEquals("Bộ bài", kq.getCategoryName()),
                    () -> assertEquals("bo-bai", kq.getCategorySlug()));
            verify(activityLogService).record(eq(adminId), anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("Chữ Đ hoa cũng thành d trong slug")
        void slugChuDHoa() {
            assertEquals("dia-chi-mua", service.create(adminId, yeuCau("Địa chỉ mua")).getSlug());
        }

        @Test
        @DisplayName("Tên toàn ký tự lạ thì slug rơi về mặc định chứ không rỗng")
        void slugRong() {
            // Slug rỗng nghĩa là một URL dạng /products/ — không mở được sản
            // phẩm nào và cũng không báo lỗi gì rõ ràng.
            assertEquals("san-pham", service.create(adminId, yeuCau("!!! ??? ***")).getSlug());
        }

        @Test
        @DisplayName("Slug trùng thì thêm số đuôi, không đè lên nhau")
        void slugTrung() {
            when(productRepository.existsBySlug("bo-bai-tarot")).thenReturn(true);
            when(productRepository.existsBySlug("bo-bai-tarot-2")).thenReturn(true);
            when(productRepository.existsBySlug("bo-bai-tarot-3")).thenReturn(false);

            assertEquals("bo-bai-tarot-3", service.create(adminId, yeuCau("Bộ bài Tarot")).getSlug());
        }

        @Test
        @DisplayName("Sửa tên KHÔNG đổi slug")
        void suaTenKhongDoiSlug() {
            Product p = sanPham("Tên cũ", "ten-cu");

            var kq = service.update(adminId, p.getId(), yeuCau("Tên hoàn toàn mới"));

            // Đổi slug là làm hỏng mọi đường dẫn đã chia sẻ, mà với shop liên
            // kết thì đường dẫn đã chia sẻ chính là kênh kiếm tiền.
            assertAll(
                    () -> assertEquals("Tên hoàn toàn mới", kq.getName()),
                    () -> assertEquals("ten-cu", kq.getSlug()));
        }

        @Test
        @DisplayName("Sửa ghi nhật ký cả giá trị cũ và mới của tên và liên kết")
        void suaGhiNhatKy() {
            Product p = sanPham("Tên cũ", "ten-cu");
            SaveProductRequest r = yeuCau("Tên mới");
            r.setAffiliateUrl("https://lazada.vn/x");
            r.setAffiliatePlatform("LAZADA");

            service.update(adminId, p.getId(), r);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Map<String, Object>> bat =
                    ArgumentCaptor.forClass(java.util.Map.class);
            verify(activityLogService).record(eq(adminId), anyString(), anyString(), any(), bat.capture());
            // Nhật ký chỉ ghi "đã sửa" thì về sau không ai truy được sửa cái gì.
            assertAll(
                    () -> assertTrue(bat.getValue().containsKey("name")),
                    () -> assertTrue(bat.getValue().containsKey("affiliateUrl")));
        }

        @Test
        @DisplayName("Sửa mà không đổi gì thì nhật ký không bịa ra thay đổi")
        void suaKhongDoiGi() {
            Product p = sanPham("Bộ bài Tarot", "bo-bai-tarot");

            service.update(adminId, p.getId(), yeuCau("Bộ bài Tarot"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Map<String, Object>> bat =
                    ArgumentCaptor.forClass(java.util.Map.class);
            verify(activityLogService).record(eq(adminId), anyString(), anyString(), any(), bat.capture());
            assertTrue(bat.getValue().isEmpty());
        }

        @Test
        @DisplayName("Liên kết KHÔNG phải http(s) thì bị chặn")
        void lienKetSaiGiaoThuc() {
            SaveProductRequest r = yeuCau("Sản phẩm");
            r.setAffiliateUrl("shopee.vn/product/1");

            // Để lọt thì khách bấm ra trang trắng, và lượt bấm ấy mất luôn.
            assertThrows(IllegalArgumentException.class, () -> service.create(adminId, r));
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Liên kết rỗng thì lưu null, không lưu chuỗi trắng")
        void lienKetRong() {
            SaveProductRequest r = yeuCau("Sản phẩm");
            r.setAffiliateUrl("   ");

            // Chuỗi trắng lọt vào DB thì countByAffiliateUrlIsNull đếm sai, và
            // màn thống kê báo "đã gắn link" cho sản phẩm chưa gắn.
            assertNull(service.create(adminId, r).getAffiliateUrl());
        }

        @Test
        @DisplayName("Sàn không nằm trong danh sách cho phép thì bị chặn")
        void sanKhongHopLe() {
            SaveProductRequest r = yeuCau("Sản phẩm");
            r.setAffiliatePlatform("AMAZON");

            // CHECK constraint ở V2_2 sẽ chặn, nhưng lúc đó lỗi là một
            // DataIntegrityViolation khó đọc thay vì một câu tiếng Việt.
            var loi = assertThrows(IllegalArgumentException.class, () -> service.create(adminId, r));
            assertTrue(loi.getMessage().contains("AMAZON"));
        }

        @Test
        @DisplayName("Không khai sàn thì mặc định SHOPEE")
        void sanMacDinh() {
            SaveProductRequest r = yeuCau("Sản phẩm");
            r.setAffiliatePlatform(null);

            assertEquals("SHOPEE", service.create(adminId, r).getAffiliatePlatform());
        }

        @Test
        @DisplayName("Không khai hoa hồng thì là 0, không phải null")
        void hoaHongMacDinh() {
            SaveProductRequest r = yeuCau("Sản phẩm");
            r.setCommissionPercent(null);

            // null lọt xuống phép nhân ở stats() là NullPointerException.
            assertEquals(BigDecimal.ZERO, service.create(adminId, r).getCommissionPercent());
        }

        @Test
        @DisplayName("Không chọn danh mục thì để trống, không nổ")
        void khongChonDanhMuc() {
            SaveProductRequest r = yeuCau("Sản phẩm");
            r.setCategoryId(null);

            assertNull(service.create(adminId, r).getCategoryName());
        }

        @Test
        @DisplayName("Danh mục không tồn tại thì báo đúng loại lỗi")
        void danhMucKhongTonTai() {
            SaveProductRequest r = yeuCau("Sản phẩm");
            r.setCategoryId(UUID.randomUUID());
            when(categoryRepository.findById(r.getCategoryId())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.create(adminId, r));
        }

        @Test
        @DisplayName("Sản phẩm không tồn tại thì báo đúng loại lỗi")
        void sanPhamKhongTonTai() {
            UUID la = UUID.randomUUID();
            when(productRepository.findById(la)).thenReturn(Optional.empty());

            assertAll(
                    () -> assertThrows(ResourceNotFoundException.class,
                            () -> service.update(adminId, la, yeuCau("x"))),
                    () -> assertThrows(ResourceNotFoundException.class,
                            () -> service.setActive(adminId, la, false)));
        }

        @Test
        @DisplayName("Ẩn sản phẩm chứ không xoá")
        void anSanPham() {
            Product p = sanPham("Bộ bài", "bo-bai-x");

            var kq = service.setActive(adminId, p.getId(), false);

            // Xoá là mất luôn số liệu đo được, mà những lượt bấm đó vẫn có thể
            // đang sinh hoa hồng ở sàn.
            assertAll(
                    () -> assertEquals(10L, kq.getClickCount()),
                    () -> assertFalse(p.getActive()));
            verify(productRepository, never()).delete(any());
        }
    }

    // =====================================================================
    // Lượt bấm
    // =====================================================================

    @Nested
    @DisplayName("Lượt bấm sang sàn")
    class LuotBam {

        @Test
        @DisplayName("Ghi lượt bấm, cộng dồn bộ đếm, trả về đúng liên kết")
        void ghiLuotBam() {
            Product p = sanPham("Bộ bài", "bo-bai-x");

            String url = service.registerClick(null, "bo-bai-x", "https://astrotarot.date/shop");

            assertAll(
                    () -> assertEquals("https://shopee.vn/product/1", url),
                    // Cộng dồn ngay trên sản phẩm để xếp hạng khỏi gom cả bảng.
                    () -> assertEquals(11L, p.getClickCount()));
            verify(clickRepository).save(any(ProductClick.class));
        }

        @Test
        @DisplayName("Khách vãng lai vẫn ghi được lượt bấm, người dùng để trống")
        void khachVangLai() {
            sanPham("Bộ bài", "bo-bai-x");

            service.registerClick(null, "bo-bai-x", null);

            ArgumentCaptor<ProductClick> bat = ArgumentCaptor.forClass(ProductClick.class);
            verify(clickRepository).save(bat.capture());
            // Bắt buộc đăng nhập mới đếm được thì mất phần lớn số liệu.
            assertAll(
                    () -> assertNull(bat.getValue().getUser()),
                    () -> assertNull(bat.getValue().getReferrer()));
        }

        @Test
        @DisplayName("Người dùng đã đăng nhập thì gắn vào lượt bấm")
        void nguoiDungDangNhap() {
            sanPham("Bộ bài", "bo-bai-x");
            User u = new User();
            u.setId(UUID.randomUUID());
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

            service.registerClick(u.getId(), "bo-bai-x", "web");

            ArgumentCaptor<ProductClick> bat = ArgumentCaptor.forClass(ProductClick.class);
            verify(clickRepository).save(bat.capture());
            assertEquals(u, bat.getValue().getUser());
        }

        @Test
        @DisplayName("ID người dùng lạ thì vẫn ghi lượt bấm, chỉ là không gắn ai")
        void idNguoiDungLa() {
            sanPham("Bộ bài", "bo-bai-x");
            UUID la = UUID.randomUUID();
            when(userRepository.findById(la)).thenReturn(Optional.empty());

            // Token cũ của tài khoản đã xoá không được làm hỏng một lượt bấm
            // vốn đã xảy ra rồi.
            assertEquals("https://shopee.vn/product/1", service.registerClick(la, "bo-bai-x", null));
            verify(clickRepository).save(any(ProductClick.class));
        }

        @Test
        @DisplayName("Referrer dài quá thì cắt còn 255 ký tự, không nổ ở tầng DB")
        void referrerQuaDai() {
            sanPham("Bộ bài", "bo-bai-x");
            String dai = "https://x/" + "a".repeat(500);

            service.registerClick(null, "bo-bai-x", dai);

            ArgumentCaptor<ProductClick> bat = ArgumentCaptor.forClass(ProductClick.class);
            verify(clickRepository).save(bat.capture());
            assertEquals(255, bat.getValue().getReferrer().length());
        }

        @Test
        @DisplayName("Sản phẩm chưa gắn liên kết thì báo lỗi, không đếm lượt bấm")
        void chuaGanLienKet() {
            Product p = sanPham("Bộ bài", "bo-bai-x");
            p.setAffiliateUrl(null);

            assertThrows(IllegalArgumentException.class,
                    () -> service.registerClick(null, "bo-bai-x", null));
            assertEquals(10L, p.getClickCount());
        }

        @Test
        @DisplayName("Sản phẩm đã ẩn thì không đếm lượt bấm")
        void sanPhamDaAn() {
            when(productRepository.findBySlugAndActiveTrue("da-an")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.registerClick(null, "da-an", null));
        }
    }

    // =====================================================================
    // Thống kê
    // =====================================================================

    @Nested
    @DisplayName("Thống kê liên kết")
    class ThongKe {

        @Test
        @DisplayName("Hoa hồng ước lượng = giá × tỉ lệ × lượt bấm")
        void hoaHongUocLuong() {
            Product p = sanPham("Bộ bài", "bo-bai-x"); // 350.000 đ, 5%, 10 lượt
            when(productRepository.findTopClicked()).thenReturn(List.of(p));
            when(clickRepository.count()).thenReturn(42L);
            when(clickRepository.countByCreatedAtAfter(any())).thenReturn(7L);
            when(productRepository.countByActiveTrueAndAffiliateUrlIsNotNull()).thenReturn(3L);
            when(productRepository.countByActiveTrueAndAffiliateUrlIsNull()).thenReturn(1L);

            var kq = service.stats(30);

            assertAll(
                    // 350.000 × 5% = 17.500, × 10 lượt = 175.000.
                    () -> assertEquals(175_000L, kq.getEstimatedCommission()),
                    () -> assertEquals(42L, kq.getTotalClicks()),
                    () -> assertEquals(7L, kq.getClicksInPeriod()),
                    () -> assertEquals(30, kq.getPeriodDays()),
                    () -> assertEquals(3L, kq.getProductsWithLink()),
                    () -> assertEquals(1L, kq.getProductsWithoutLink()));
        }

        @Test
        @DisplayName("Sản phẩm thiếu giá hoặc thiếu tỉ lệ thì tính 0, không nổ")
        void thieuGiaHoacTiLe() {
            Product thieuGia = sanPham("A", "a");
            thieuGia.setPrice(null);
            Product thieuTiLe = sanPham("B", "b");
            thieuTiLe.setCommissionPercent(null);
            when(productRepository.findTopClicked()).thenReturn(List.of(thieuGia, thieuTiLe));

            // Một sản phẩm khai thiếu không được làm hỏng cả trang thống kê.
            assertEquals(0L, service.stats(7).getEstimatedCommission());
        }

        @Test
        @DisplayName("Bảng xếp hạng cắt ở 10 sản phẩm")
        void catOMuoi() {
            List<Product> nhieu = new java.util.ArrayList<>();
            for (int i = 0; i < 25; i++) {
                nhieu.add(sanPham("SP " + i, "sp-" + i));
            }
            when(productRepository.findTopClicked()).thenReturn(nhieu);

            assertEquals(10, service.stats(30).getTopProducts().size());
        }

        @Test
        @DisplayName("Chưa có lượt bấm nào thì trả 0 chứ không null")
        void chuaCoLuotBamNao() {
            when(productRepository.findTopClicked()).thenReturn(List.of());

            var kq = service.stats(30);

            assertAll(
                    () -> assertEquals(0L, kq.getEstimatedCommission()),
                    () -> assertTrue(kq.getTopProducts().isEmpty()));
        }
    }
}
