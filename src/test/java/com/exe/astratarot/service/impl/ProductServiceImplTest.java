package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.Product;
import com.exe.astratarot.domain.entity.ProductCategory;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.ProductCategoryRepository;
import com.exe.astratarot.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Gian hàng phía khách. Lớp này trước đây phủ 0,0%.
 *
 * <p>Điểm đáng kiểm nhất nằm ở chỗ ít ai ngờ: <b>chuỗi rỗng là sentinel</b>.
 * Truy vấn ở {@code ProductRepository.search} viết {@code :keyword = ''} để
 * nghĩa là "không lọc". Nên service phải quy null và chuỗi trắng về đúng chuỗi
 * rỗng — truyền null xuống thì điều kiện so sánh với null luôn sai trong SQL và
 * danh sách trả về rỗng, một lỗi trông y hệt "chưa có sản phẩm nào".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductCategoryRepository categoryRepository;

    private ProductServiceImpl service;
    private Product sanPham;

    @BeforeEach
    void setUp() {
        service = new ProductServiceImpl(productRepository, categoryRepository);

        sanPham = Product.builder()
                .id(UUID.randomUUID())
                .name("Bộ bài Thoth")
                .slug("bo-bai-thoth")
                .description("Mô tả")
                .price(420_000L)
                .compareAtPrice(500_000L)
                .stock(3)
                .imageUrl("https://cdn/thoth.jpg")
                .imageIsIllustrative(true)
                .affiliateUrl("https://shopee.vn/x")
                .affiliatePlatform("SHOPEE")
                .commissionPercent(new BigDecimal("4.5"))
                .clickCount(12L)
                .featured(true)
                .active(true)
                .category(ProductCategory.builder()
                        .id(UUID.randomUUID()).name("Bộ bài").slug("bo-bai").build())
                .build();

        lenient().when(productRepository.search(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sanPham)));
        lenient().when(productRepository.searchForAdmin(any(), any()))
                .thenReturn(new PageImpl<>(List.of(sanPham)));
    }

    // =====================================================================

    @Test
    @DisplayName("Không lọc gì thì truyền CHUỖI RỖNG xuống, không truyền null")
    void khongLocThiChuoiRong() {
        service.list(null, null, PageRequest.of(0, 10));

        // Truy vấn dùng :keyword = '' làm sentinel. Truyền null xuống thì điều
        // kiện luôn sai trong SQL và danh sách về rỗng — trông y hệt "chưa có
        // sản phẩm nào", nên rất lâu mới có người nhận ra.
        org.mockito.Mockito.verify(productRepository).search(eq(""), eq(""), any());
    }

    @Test
    @DisplayName("Chuỗi toàn dấu cách cũng là không lọc")
    void chuoiTrangCungLaKhongLoc() {
        service.list("   ", "   ", PageRequest.of(0, 10));

        org.mockito.Mockito.verify(productRepository).search(eq(""), eq(""), any());
    }

    @Test
    @DisplayName("Từ khoá và danh mục được cắt hai đầu trước khi tìm")
    void catHaiDau() {
        service.list("  bo-bai  ", "  thoth  ", PageRequest.of(0, 10));

        // Dấu cách thừa từ ô tìm kiếm mà lọt xuống LIKE thì không khớp gì cả.
        org.mockito.Mockito.verify(productRepository).search(eq("bo-bai"), eq("thoth"), any());
    }

    @Test
    @DisplayName("Danh sách mang đủ trường cho thẻ sản phẩm")
    void duTruong() {
        var trang = service.list(null, null, PageRequest.of(0, 10));
        var sp = trang.getContent().get(0);

        assertAll(
                () -> assertEquals("Bộ bài Thoth", sp.getName()),
                () -> assertEquals("bo-bai-thoth", sp.getSlug()),
                () -> assertEquals(420_000L, sp.getPrice()),
                () -> assertEquals(500_000L, sp.getCompareAtPrice()),
                () -> assertEquals(3, sp.getStock()),
                () -> assertTrue(sp.getImageIsIllustrative()),
                () -> assertTrue(sp.getFeatured()),
                () -> assertEquals(12L, sp.getClickCount()),
                () -> assertEquals("Bộ bài", sp.getCategoryName()),
                () -> assertEquals("bo-bai", sp.getCategorySlug()));
    }

    @Test
    @DisplayName("Sản phẩm không có danh mục thì để trống, không nổ")
    void khongCoDanhMuc() {
        sanPham.setCategory(null);

        var sp = service.list(null, null, PageRequest.of(0, 10)).getContent().get(0);

        assertAll(
                () -> assertNull(sp.getCategoryName()),
                () -> assertNull(sp.getCategorySlug()));
    }

    @Test
    @DisplayName("Xem theo slug: chỉ sản phẩm đang bán")
    void xemTheoSlug() {
        when(productRepository.findBySlugAndActiveTrue("bo-bai-thoth"))
                .thenReturn(Optional.of(sanPham));

        assertEquals("Bộ bài Thoth", service.getBySlug("bo-bai-thoth").getName());
    }

    @Test
    @DisplayName("Slug không có thật thì báo kèm chính slug đó")
    void slugKhongCoThat() {
        when(productRepository.findBySlugAndActiveTrue("khong-ton-tai"))
                .thenReturn(Optional.empty());

        var loi = assertThrows(ResourceNotFoundException.class,
                () -> service.getBySlug("khong-ton-tai"));
        // Nêu slug trong câu lỗi để đọc log là biết ngay đường dẫn nào hỏng.
        assertTrue(loi.getMessage().contains("khong-ton-tai"));
    }

    @Test
    @DisplayName("Sản phẩm nổi bật")
    void noiBat() {
        when(productRepository.findByActiveTrueAndFeaturedTrue()).thenReturn(List.of(sanPham));

        assertEquals(1, service.getFeatured().size());
    }

    @Test
    @DisplayName("Chưa có sản phẩm nổi bật thì trả danh sách rỗng, không null")
    void chuaCoNoiBat() {
        when(productRepository.findByActiveTrueAndFeaturedTrue()).thenReturn(List.of());

        assertTrue(service.getFeatured().isEmpty());
    }

    @Test
    @DisplayName("Danh mục trả theo đúng thứ tự hiển thị đã khai")
    void danhMucTheoThuTu() {
        when(categoryRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(
                ProductCategory.builder().id(UUID.randomUUID())
                        .name("Bộ bài").slug("bo-bai").displayOrder(1).build(),
                ProductCategory.builder().id(UUID.randomUUID())
                        .name("Phụ kiện").slug("phu-kien").displayOrder(2).build()));

        var dm = service.getCategories();

        assertAll(
                () -> assertEquals(2, dm.size()),
                () -> assertEquals("Bộ bài", dm.get(0).getName()),
                () -> assertEquals(1, dm.get(0).getDisplayOrder()));
    }

    @Test
    @DisplayName("Danh sách cho quản trị cũng quy null về chuỗi rỗng")
    void danhSachQuanTri() {
        service.listForAdmin(null, PageRequest.of(0, 20));
        service.listForAdmin("  bo bai  ", PageRequest.of(0, 20));

        org.mockito.Mockito.verify(productRepository).searchForAdmin(eq(""), any());
        org.mockito.Mockito.verify(productRepository).searchForAdmin(eq("bo bai"), any());
    }
}
