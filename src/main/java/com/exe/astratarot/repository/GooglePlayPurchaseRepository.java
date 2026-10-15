package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.GooglePlayPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GooglePlayPurchaseRepository
        extends JpaRepository<GooglePlayPurchase, String> {
}
