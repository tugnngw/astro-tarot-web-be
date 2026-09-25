package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.blog.CreateBlogRequest;
import com.exe.astratarot.domain.dto.blog.ReviewBlogRequest;
import com.exe.astratarot.domain.dto.blog.UpdateBlogRequest;
import com.exe.astratarot.domain.entity.Blog;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BlogStatus;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BlogRepository;
import com.exe.astratarot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Luồng duyệt bài viết — phần bộ kiểm cũ chưa chạm tới: tạo, xem, xoá, đọc
 * danh sách, và các nhánh còn lại của việc duyệt.
 *
 * <p>Bài viết đi qua năm trạng thái: DRAFT → PENDING → APPROVED → PUBLISHED, và
 * REJECTED rẽ ngang. Hai chỗ đáng kiểm hơn cả:
 *
 * <ol>
 *   <li><b>Danh sách công khai CHỈ trả bài đã xuất bản.</b> Tham số trạng thái
 *       từ phía client bị bỏ qua hoàn toàn. Nếu tin nó thì ai cũng gọi
 *       {@code ?status=DRAFT} và đọc được bản nháp của người khác — nội dung
 *       chưa ai duyệt, đăng ra ngoài dưới tên thương hiệu.
 *   <li><b>Từ chối phải kèm lý do.</b> Từ chối mà không nói vì sao là bắt người
 *       viết sửa mò, và họ sẽ gửi lại đúng bài ấy.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlogServiceImplWorkflowTest {

    @Mock private BlogRepository blogRepository;
    @Mock private UserRepository userRepository;

    private BlogServiceImpl service;

    private User tacGia;
    private User nhanVien;
    private User quanLy;
    private User nguoiLa;

    @BeforeEach
    void setUp() {
        service = new BlogServiceImpl(blogRepository, userRepository);

        tacGia = taiKhoan("Tác giả", UserRole.USER);
        nhanVien = taiKhoan("Nhân viên", UserRole.STAFF);
        quanLy = taiKhoan("Quản lý", UserRole.MANAGER);
        nguoiLa = taiKhoan("Người lạ", UserRole.USER);

        lenient().when(blogRepository.existsBySlug(anyString())).thenReturn(false);
        lenient().when(blogRepository.save(any(Blog.class))).thenAnswer(inv -> {
            Blog b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(UUID.randomUUID());
            }
            return b;
        });
    }

    private User taiKhoan(String ten, UserRole vaiTro) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setFullName(ten);
        u.setRole(vaiTro);
        lenient().when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private Blog baiViet(BlogStatus trangThai) {
        Blog b = Blog.builder()
                .id(UUID.randomUUID())
                .title("Ba lá bài cho câu hỏi nghề nghiệp")
                .slug("ba-la-bai-cho-cau-hoi-nghe-nghiep")
                .summary("Tóm tắt")
                .content("Nội dung")
                .author(tacGia)
                .status(trangThai)
                .build();
        lenient().when(blogRepository.findById(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    private ReviewBlogRequest duyet(String hanhDong, String lyDo) {
        ReviewBlogRequest r = new ReviewBlogRequest();
        r.setAction(hanhDong);
        r.setRejectionReason(lyDo);
        return r;
    }

    // =====================================================================
    // Tạo
    // =====================================================================

    @Nested
    @DisplayName("Tạo bài")
    class TaoBai {

        private CreateBlogRequest yeuCau(String slug) {
            CreateBlogRequest r = new CreateBlogRequest();
            r.setTitle("Tiêu đề");
            r.setSlug(slug);
            r.setSummary("Tóm tắt");
            r.setContent("Nội dung");
            r.setThumbnailUrl("https://cdn/a.jpg");
            return r;
        }

        @Test
        @DisplayName("Bài mới luôn ở DRAFT, không bao giờ xuất bản thẳng")
        void baiMoiLaDraft() {
            var kq = service.create(tacGia.getId(), yeuCau("bai-moi"));

            // Cho tạo thẳng ở PUBLISHED là mở đường đi vòng qua toàn bộ khâu
            // duyệt, và khâu duyệt là lý do tồn tại của luồng này.
            assertAll(
                    () -> assertEquals(BlogStatus.DRAFT, kq.getStatus()),
                    () -> assertEquals("Tác giả", kq.getAuthor().getFullName()),
                    () -> assertNull(kq.getReviewer()));
        }

        @Test
        @DisplayName("Slug trùng thì bị chặn, và câu lỗi nêu chính slug đó")
        void slugTrung() {
            when(blogRepository.existsBySlug("da-ton-tai")).thenReturn(true);

            var loi = assertThrows(IllegalArgumentException.class,
                    () -> service.create(tacGia.getId(), yeuCau("da-ton-tai")));
            assertTrue(loi.getMessage().contains("da-ton-tai"));
            verify(blogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Tài khoản không tồn tại thì không tạo được bài")
        void taiKhoanKhongTonTai() {
            UUID la = UUID.randomUUID();
            when(userRepository.findById(la)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.create(la, yeuCau("bai-moi")));
        }
    }

    // =====================================================================
    // Xem
    // =====================================================================

    @Nested
    @DisplayName("Quyền xem")
    class QuyenXem {

        @Test
        @DisplayName("Bài ĐÃ XUẤT BẢN thì ai cũng xem được")
        void baiDaXuatBan() {
            Blog b = baiViet(BlogStatus.PUBLISHED);

            assertEquals(b.getId(), service.get(nguoiLa.getId(), b.getId()).getId());
        }

        @Test
        @DisplayName("Bản NHÁP: chỉ tác giả và nhân sự xem được")
        void banNhap() {
            Blog b = baiViet(BlogStatus.DRAFT);

            assertAll(
                    () -> assertEquals(b.getId(), service.get(tacGia.getId(), b.getId()).getId()),
                    () -> assertEquals(b.getId(), service.get(nhanVien.getId(), b.getId()).getId()),
                    // Bản nháp là nội dung chưa ai duyệt, mang tên thương hiệu.
                    () -> assertThrows(AccessDeniedException.class,
                            () -> service.get(nguoiLa.getId(), b.getId())));
        }

        @Test
        @DisplayName("Bài không tồn tại thì báo đúng loại lỗi")
        void baiKhongTonTai() {
            UUID la = UUID.randomUUID();
            when(blogRepository.findById(la)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.get(tacGia.getId(), la));
        }
    }

    // =====================================================================
    // Sửa và xoá
    // =====================================================================

    @Nested
    @DisplayName("Sửa và xoá")
    class SuaVaXoa {

        private UpdateBlogRequest sua(String tieuDe, String slug) {
            UpdateBlogRequest r = new UpdateBlogRequest();
            r.setTitle(tieuDe);
            r.setSlug(slug);
            return r;
        }

        @Test
        @DisplayName("Trường không gửi lên thì giữ nguyên")
        void truongKhongGuiGiuNguyen() {
            Blog b = baiViet(BlogStatus.DRAFT);

            var kq = service.update(tacGia.getId(), b.getId(), sua("Tiêu đề mới", null));

            assertAll(
                    () -> assertEquals("Tiêu đề mới", kq.getTitle()),
                    () -> assertEquals("ba-la-bai-cho-cau-hoi-nghe-nghiep", kq.getSlug()),
                    () -> assertEquals("Nội dung", kq.getContent()));
        }

        @Test
        @DisplayName("Đổi slug sang slug đã có thì bị chặn")
        void doiSangSlugDaCo() {
            Blog b = baiViet(BlogStatus.DRAFT);
            when(blogRepository.existsBySlug("slug-cua-nguoi-khac")).thenReturn(true);

            assertThrows(IllegalArgumentException.class,
                    () -> service.update(tacGia.getId(), b.getId(), sua(null, "slug-cua-nguoi-khac")));
        }

        @Test
        @DisplayName("Gửi lại ĐÚNG slug cũ thì không coi là trùng")
        void guiLaiDungSlugCu() {
            Blog b = baiViet(BlogStatus.DRAFT);
            when(blogRepository.existsBySlug(anyString())).thenReturn(true);

            // Giao diện gửi cả form, gồm cả slug không đổi. Chặn ở đây là khoá
            // luôn việc sửa tiêu đề.
            service.update(tacGia.getId(), b.getId(), sua("Tiêu đề mới", b.getSlug()));
            assertEquals("Tiêu đề mới", b.getTitle());
        }

        @Test
        @DisplayName("Người lạ không sửa được bài của người khác")
        void nguoiLaKhongSuaDuoc() {
            Blog b = baiViet(BlogStatus.DRAFT);

            assertThrows(AccessDeniedException.class,
                    () -> service.update(nguoiLa.getId(), b.getId(), sua("Bịa", null)));
        }

        @Test
        @DisplayName("Nhân sự KHÔNG sửa được bài đã xuất bản")
        void nhanSuKhongSuaBaiDaXuatBan() {
            Blog b = baiViet(BlogStatus.PUBLISHED);

            // Sửa thẳng bài đang hiện ngoài trang là đổi nội dung công khai mà
            // không qua một vòng duyệt nào.
            assertThrows(IllegalArgumentException.class,
                    () -> service.update(nhanVien.getId(), b.getId(), sua("Sửa lén", null)));
        }

        @Test
        @DisplayName("Sửa bài bị TỪ CHỐI thì xoá lý do từ chối cũ")
        void suaBaiBiTuChoi() {
            Blog b = baiViet(BlogStatus.REJECTED);
            b.setRejectionReason("Thiếu dẫn nguồn");

            var kq = service.update(tacGia.getId(), b.getId(), sua("Đã bổ sung nguồn", null));

            // Giữ lại lý do cũ sau khi đã sửa là để một lời chê treo trên một
            // bài đã khác hẳn.
            assertNull(kq.getRejectionReason());
        }

        @Test
        @DisplayName("Tác giả chỉ xoá được BẢN NHÁP")
        void tacGiaChiXoaDuocBanNhap() {
            Blog nhap = baiViet(BlogStatus.DRAFT);
            service.delete(tacGia.getId(), nhap.getId());
            verify(blogRepository).delete(nhap);

            Blog dangCho = baiViet(BlogStatus.PENDING);
            // Xoá bài đang chờ duyệt là rút bài khỏi hàng đợi của người duyệt
            // mà họ không biết.
            assertThrows(IllegalArgumentException.class,
                    () -> service.delete(tacGia.getId(), dangCho.getId()));
        }

        @Test
        @DisplayName("Nhân sự xoá được bài ở mọi trạng thái")
        void nhanSuXoaDuocMoiTrangThai() {
            Blog b = baiViet(BlogStatus.PUBLISHED);

            service.delete(nhanVien.getId(), b.getId());

            verify(blogRepository).delete(b);
        }

        @Test
        @DisplayName("Người lạ không xoá được bài của người khác")
        void nguoiLaKhongXoaDuoc() {
            Blog b = baiViet(BlogStatus.DRAFT);

            assertThrows(AccessDeniedException.class,
                    () -> service.delete(nguoiLa.getId(), b.getId()));
            verify(blogRepository, never()).delete(any());
        }
    }

    // =====================================================================
    // Gửi duyệt và duyệt
    // =====================================================================

    @Nested
    @DisplayName("Gửi duyệt và duyệt")
    class GuiDuyetVaDuyet {

        @Test
        @DisplayName("Chỉ tác giả gửi được bài đi duyệt")
        void chiTacGiaGuiDuoc() {
            Blog b = baiViet(BlogStatus.DRAFT);

            assertThrows(AccessDeniedException.class,
                    () -> service.submitForReview(nhanVien.getId(), b.getId()));
        }

        @Test
        @DisplayName("Gửi lại được bài đã bị TỪ CHỐI")
        void guiLaiBaiBiTuChoi() {
            Blog b = baiViet(BlogStatus.REJECTED);

            // Từ chối không phải là dấu chấm hết; sửa rồi gửi lại là cả mục
            // đích của việc nêu lý do từ chối.
            assertEquals(BlogStatus.PENDING,
                    service.submitForReview(tacGia.getId(), b.getId()).getStatus());
        }

        @Test
        @DisplayName("Không gửi lại được bài ĐANG CHỜ hoặc đã xuất bản")
        void khongGuiLaiBaiDangCho() {
            for (BlogStatus tt : List.of(BlogStatus.PENDING, BlogStatus.APPROVED,
                    BlogStatus.PUBLISHED)) {
                Blog b = baiViet(tt);
                assertThrows(IllegalArgumentException.class,
                        () -> service.submitForReview(tacGia.getId(), b.getId()),
                        "trạng thái " + tt + " phải bị chặn");
            }
        }

        @Test
        @DisplayName("Nhân viên KHÔNG duyệt được bài, chỉ Quản lý trở lên")
        void nhanVienKhongDuyetDuoc() {
            Blog b = baiViet(BlogStatus.PENDING);

            assertThrows(AccessDeniedException.class,
                    () -> service.review(nhanVien.getId(), b.getId(), duyet("APPROVED", null)));
        }

        @Test
        @DisplayName("Duyệt bài KHÔNG ở trạng thái chờ thì bị chặn")
        void duyetBaiKhongOTrangThaiCho() {
            Blog b = baiViet(BlogStatus.DRAFT);

            assertThrows(IllegalStateException.class,
                    () -> service.review(quanLy.getId(), b.getId(), duyet("APPROVED", null)));
        }

        @Test
        @DisplayName("Từ chối mà không nêu lý do thì bị chặn")
        void tuChoiKhongNeuLyDo() {
            Blog b = baiViet(BlogStatus.PENDING);

            // Từ chối mà không nói vì sao là bắt người viết sửa mò, và họ sẽ
            // gửi lại đúng bài ấy.
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.review(quanLy.getId(), b.getId(), duyet("REJECTED", null))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.review(quanLy.getId(), b.getId(), duyet("REJECTED", "  "))));
        }

        @Test
        @DisplayName("Từ chối: lưu lý do đã cắt hai đầu và ghi người duyệt")
        void tuChoiLuuLyDo() {
            Blog b = baiViet(BlogStatus.PENDING);

            var kq = service.review(quanLy.getId(), b.getId(), duyet("REJECTED", "  Thiếu dẫn nguồn  "));

            assertAll(
                    () -> assertEquals(BlogStatus.REJECTED, kq.getStatus()),
                    () -> assertEquals("Thiếu dẫn nguồn", kq.getRejectionReason()),
                    () -> assertNotNull(kq.getReviewer()),
                    () -> assertEquals("Quản lý", kq.getReviewer().getFullName()));
        }

        @Test
        @DisplayName("Chỉ XUẤT BẢN được bài đã duyệt")
        void chiXuatBanBaiDaDuyet() {
            Blog daDuyet = baiViet(BlogStatus.APPROVED);
            assertEquals(BlogStatus.PUBLISHED,
                    service.review(quanLy.getId(), daDuyet.getId(), duyet("PUBLISH", null)).getStatus());

            Blog dangCho = baiViet(BlogStatus.PENDING);
            // Xuất bản thẳng từ hàng chờ là bỏ qua bước duyệt nội dung.
            assertThrows(IllegalArgumentException.class,
                    () -> service.review(quanLy.getId(), dangCho.getId(), duyet("PUBLISH", null)));
        }

        @Test
        @DisplayName("Hành động nhận chữ thường và dấu cách thừa")
        void hanhDongChuThuong() {
            Blog b = baiViet(BlogStatus.PENDING);

            assertEquals(BlogStatus.APPROVED,
                    service.review(quanLy.getId(), b.getId(), duyet("  approved  ", null)).getStatus());
        }

        @Test
        @DisplayName("Hành động không có thật thì báo lỗi kèm chính chữ đó")
        void hanhDongKhongCoThat() {
            Blog b = baiViet(BlogStatus.PENDING);

            var loi = assertThrows(IllegalArgumentException.class,
                    () -> service.review(quanLy.getId(), b.getId(), duyet("XOA_LUON", null)));
            assertTrue(loi.getMessage().contains("XOA_LUON"));
        }
    }

    // =====================================================================
    // Danh sách
    // =====================================================================

    @Nested
    @DisplayName("Danh sách")
    class DanhSach {

        @Test
        @DisplayName("Danh sách CÔNG KHAI bỏ qua tham số trạng thái của client")
        void congKhaiBoQuaThamSo() {
            when(blogRepository.findByStatus(any(), any())).thenReturn(new PageImpl<>(List.of()));

            service.listPublic(BlogStatus.DRAFT, PageRequest.of(0, 10));

            // Tin tham số từ client thì ai cũng gọi ?status=DRAFT và đọc được
            // bản nháp của người khác.
            verify(blogRepository).findByStatus(
                    org.mockito.ArgumentMatchers.eq(BlogStatus.PUBLISHED), any());
        }

        @Test
        @DisplayName("Danh sách công khai mang đủ thông tin phân trang")
        void congKhaiDuPhanTrang() {
            Blog b = baiViet(BlogStatus.PUBLISHED);
            when(blogRepository.findByStatus(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(b), PageRequest.of(0, 10), 25));

            var kq = service.listPublic(null, PageRequest.of(0, 10));

            assertAll(
                    () -> assertEquals(1, kq.getContent().size()),
                    () -> assertEquals(25L, kq.getTotalElements()),
                    () -> assertEquals(3, kq.getTotalPages()),
                    () -> assertEquals(0, kq.getNumber()),
                    () -> assertEquals(10, kq.getSize()));
        }

        @Test
        @DisplayName("Danh sách của tác giả gồm cả năm trạng thái")
        void danhSachCuaTacGia() {
            when(blogRepository.findByAuthorIdAndAllowedStatus(any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            service.listByAuthor(tacGia.getId(), PageRequest.of(0, 10));

            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<java.util.Set<BlogStatus>> bat =
                    org.mockito.ArgumentCaptor.forClass(java.util.Set.class);
            verify(blogRepository).findByAuthorIdAndAllowedStatus(any(), bat.capture(), any());
            // Thiếu một trạng thái là bài của chính họ biến mất khỏi trang của
            // họ, và họ không có cách nào tìm lại.
            assertEquals(5, bat.getValue().size());
        }

        @Test
        @DisplayName("Danh sách quản trị đọc từ truy vấn có đủ chi tiết")
        void danhSachQuanTri() {
            when(blogRepository.findAllWithDetails(any())).thenReturn(new PageImpl<>(List.of()));

            assertEquals(0, service.listAll(PageRequest.of(0, 10)).getTotalElements());
            verify(blogRepository).findAllWithDetails(any());
        }
    }
}
