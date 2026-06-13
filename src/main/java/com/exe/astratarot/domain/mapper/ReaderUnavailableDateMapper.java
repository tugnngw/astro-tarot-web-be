package com.exe.astratarot.domain.mapper;

import com.exe.astratarot.domain.dto.reader.ReaderUnavailableDateResponse;
import com.exe.astratarot.domain.entity.ReaderUnavailableDate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for the {@link ReaderUnavailableDate} entity and its DTOs.
 */
@Mapper(config = MapStructConfig.class)
public interface ReaderUnavailableDateMapper {

    /**
     * Maps a {@link ReaderUnavailableDate} entity to a {@link ReaderUnavailableDateResponse} DTO.
     */
    @Mapping(source = "id", target = "id")
    ReaderUnavailableDateResponse toResponse(ReaderUnavailableDate unavailableDate);
}
