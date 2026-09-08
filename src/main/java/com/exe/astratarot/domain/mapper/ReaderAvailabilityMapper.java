package com.exe.astratarot.domain.mapper;

import com.exe.astratarot.domain.dto.reader.ReaderAvailabilityResponse;
import com.exe.astratarot.domain.entity.ReaderAvailability;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for the {@link ReaderAvailability} entity and its DTOs.
 */
@Mapper(config = MapStructConfig.class)
public interface ReaderAvailabilityMapper {

    /**
     * Maps a {@link ReaderAvailability} entity to a {@link ReaderAvailabilityResponse} DTO.
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "active", target = "isActive")
    ReaderAvailabilityResponse toResponse(ReaderAvailability availability);
}
