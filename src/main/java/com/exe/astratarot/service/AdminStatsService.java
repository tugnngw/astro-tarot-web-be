package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.admin.AdminStatsResponse;

/** Tổng hợp số liệu cho trang Quản trị. */
public interface AdminStatsService {
    AdminStatsResponse getStats();
}
