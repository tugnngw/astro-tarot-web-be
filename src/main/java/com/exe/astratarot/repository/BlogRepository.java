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
