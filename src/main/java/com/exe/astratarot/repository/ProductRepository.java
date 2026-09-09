package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySlugAndActiveTrue(String slug);

    List<Product> findByActiveTrueAndFeaturedTrue();

    /**
     * Danh sách sản phẩm đang bán, lọc tuỳ chọn theo danh mục và từ khoá.
     * Truyền chuỗi rỗng cho tham số nào muốn bỏ qua bộ lọc đó.
     *
     * Dùng chuỗi rỗng làm sentinel thay vì null là có chủ ý: với ":p IS NULL",
     * Hibernate gửi tham số null xuống mà không kèm kiểu, Postgres suy ra
     * bytea rồi vỡ ở "function lower(bytea) does not exist". Tham số luôn
     * non-null thì kiểu text được suy đúng.
     */
    @Query("""
            SELECT p FROM Product p
            LEFT JOIN FETCH p.category
            WHERE p.active = TRUE
              AND (:categorySlug = '' OR p.category.slug = :categorySlug)
              AND (:keyword = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Product> search(@Param("categorySlug") String categorySlug,
                         @Param("keyword") String keyword,
                         Pageable pageable);

    boolean existsBySlug(String slug);

    long countByActiveTrueAndAffiliateUrlIsNotNull();

    long countByActiveTrueAndAffiliateUrlIsNull();

    /** Xếp theo lượt bấm sang sàn — sản phẩm nào đáng giữ, sản phẩm nào nên bỏ. */
    @Query("""
            SELECT p FROM Product p
            LEFT JOIN FETCH p.category
            WHERE p.active = TRUE
            ORDER BY p.clickCount DESC
            """)
    List<Product> findTopClicked();

    /** Cả sản phẩm đã ẩn, cho màn quản trị. */
    @Query("""
            SELECT p FROM Product p
            LEFT JOIN FETCH p.category
            WHERE (:keyword = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY p.createdAt DESC
            """)
    Page<Product> searchForAdmin(@Param("keyword") String keyword, Pageable pageable);
}
