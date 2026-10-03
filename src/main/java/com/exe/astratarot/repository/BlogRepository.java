package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.Blog;
import com.exe.astratarot.domain.enums.BlogStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BlogRepository extends JpaRepository<Blog, UUID> {

    boolean existsBySlug(String slug);

    Optional<Blog> findBySlug(String slug);

    @Query("""
            SELECT b FROM Blog b
            JOIN FETCH b.author a
            WHERE b.id = :id
            """)
    Optional<Blog> findWithAuthorById(@Param("id") UUID id);

    @Query(value = """
            SELECT b FROM Blog b
            JOIN FETCH b.author a
            WHERE b.status = com.exe.astratarot.domain.enums.BlogStatus.PUBLISHED
              AND (:keyword = '' OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(b.summary) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(a.username) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY b.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(b) FROM Blog b
            JOIN b.author a
            WHERE b.status = com.exe.astratarot.domain.enums.BlogStatus.PUBLISHED
              AND (:keyword = '' OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(b.summary) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(a.username) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Blog> findPublicWithFilter(@Param("keyword") String keyword, Pageable pageable);

    @Query(value = """
            SELECT b FROM Blog b
            JOIN FETCH b.author a
            LEFT JOIN FETCH b.reviewer r
            WHERE (:status IS NULL OR b.status = :status)
              AND (:keyword = '' OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(b.slug) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(b.summary) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(a.username) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY b.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(b) FROM Blog b
            JOIN b.author a
            WHERE (:status IS NULL OR b.status = :status)
              AND (:keyword = '' OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(b.slug) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(b.summary) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(a.username) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Blog> findAllWithFilter(@Param("status") BlogStatus status,
                                 @Param("keyword") String keyword,
                                 Pageable pageable);

    @Query(value = """
            SELECT b FROM Blog b
            JOIN FETCH b.author a
            LEFT JOIN FETCH b.reviewer r
            WHERE b.author.id = :authorId
              AND (:status IS NULL OR b.status = :status)
              AND (:keyword = '' OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(b.slug) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(b.summary) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY b.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(b) FROM Blog b
            WHERE b.author.id = :authorId
              AND (:status IS NULL OR b.status = :status)
              AND (:keyword = '' OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(b.slug) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(b.summary) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Blog> findByAuthorIdWithFilter(@Param("authorId") UUID authorId,
                                        @Param("status") BlogStatus status,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);

    @Query("""
            SELECT b FROM Blog b
            JOIN FETCH b.author a
            WHERE b.status = :status
            ORDER BY b.createdAt DESC
            """)
    Page<Blog> findByStatus(@Param("status") BlogStatus status, Pageable pageable);

    @Query("""
            SELECT b FROM Blog b
            JOIN FETCH b.author a
            WHERE b.author.id = :authorId
            ORDER BY b.createdAt DESC
            """)
    Page<Blog> findByAuthorId(@Param("authorId") UUID authorId, Pageable pageable);

    @Query("""
            SELECT b FROM Blog b
            JOIN FETCH b.author a
            LEFT JOIN FETCH b.reviewer r
            WHERE b.status = :status
            ORDER BY b.createdAt DESC
            """)
    Page<Blog> findByStatusWithDetails(@Param("status") BlogStatus status, Pageable pageable);

    @Query("""
            SELECT b FROM Blog b
            JOIN FETCH b.author a
            WHERE b.author.id = :authorId
            AND b.status IN :allowedStatuses
            ORDER BY b.createdAt DESC
            """)
    Page<Blog> findByAuthorIdAndAllowedStatus(
            @Param("authorId") UUID authorId,
            @Param("allowedStatuses") java.util.Set<BlogStatus> allowedStatuses,
            Pageable pageable);

    @Query("""
            SELECT b FROM Blog b
            JOIN FETCH b.author a
            LEFT JOIN FETCH b.reviewer r
            ORDER BY b.createdAt DESC
            """)
    Page<Blog> findAllWithDetails(Pageable pageable);
}
