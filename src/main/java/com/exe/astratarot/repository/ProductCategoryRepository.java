package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    List<ProductCategory> findAllByOrderByDisplayOrderAsc();
    Optional<ProductCategory> findBySlug(String slug);
}
