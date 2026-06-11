package com.exe.astratarot.domain.dto.reader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReaderApplicationResponse {

    private String id;
    private String status;
    private String bio;
    private Integer experience;
    private String[] specialties;
    private Instant createdAt;
}