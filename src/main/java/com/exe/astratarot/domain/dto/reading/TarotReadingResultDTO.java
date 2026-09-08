package com.exe.astratarot.domain.dto.reading;

import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a completed AI Tarot reading.
 * Contains all relevant information about the reading, including AI interpretation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TarotReadingResultDTO {

    private UUID readingId;
    private String userQuestion;
    private String spreadName;
    private List<DrawnCardDetailDTO> drawnCards;
    private String aiInterpretation;
    private String modelUsed;
    private Integer totalTokensUsed;
    private Instant readingTimestamp;
}
