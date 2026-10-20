package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.KhuyenMaiDaChuc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KhuyenMaiDaChucRepository
        extends JpaRepository<KhuyenMaiDaChuc, KhuyenMaiDaChuc.Khoa> {

    boolean existsByKhuyenMaiIdAndUserId(UUID khuyenMaiId, UUID userId);
}
