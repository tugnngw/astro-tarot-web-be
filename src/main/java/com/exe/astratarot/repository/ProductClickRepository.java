package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ProductClick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface ProductClickRepository extends JpaRepository<ProductClick, UUID> {

    long countByCreatedAtAfter(Instant since);
}
