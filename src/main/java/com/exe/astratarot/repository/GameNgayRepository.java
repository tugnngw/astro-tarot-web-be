package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.GameNgay;
import com.exe.astratarot.domain.entity.GameNgayId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameNgayRepository extends JpaRepository<GameNgay, GameNgayId> {
}
