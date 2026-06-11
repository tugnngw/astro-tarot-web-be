package com.exe.astratarot.domain.dto.reader;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReaderRequest {

    @NotBlank(message = "Action is required")
    private String action; // APPROVED or REJECTED

    private String rejectionReason;
}