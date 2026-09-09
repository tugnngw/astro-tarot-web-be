package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.report.CreateReportRequest;
import com.exe.astratarot.domain.dto.report.HandleReportRequest;
import com.exe.astratarot.domain.dto.report.ReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReportService {

    ReportResponse create(UUID reporterId, CreateReportRequest request);

    Page<ReportResponse> list(String status, Pageable pageable);

    long pendingCount();

    ReportResponse handle(UUID actorId, UUID reportId, HandleReportRequest request);
}
