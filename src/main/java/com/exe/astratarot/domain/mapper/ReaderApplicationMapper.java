package com.exe.astratarot.domain.mapper;

import com.exe.astratarot.domain.dto.reader.ApplyReaderRequest;
import com.exe.astratarot.domain.dto.reader.ReaderApplicationResponse;
import com.exe.astratarot.domain.entity.ReaderApplication;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.UUID;

/**
 * Mapper for the {@link ReaderApplication} entity and its DTOs.
 */
@Mapper(config = MapStructConfig.class)
public interface ReaderApplicationMapper {

    /**
     * Maps an {@link ApplyReaderRequest} DTO to a {@link ReaderApplication} entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", expression = "java(com.exe.astratarot.domain.entity.ReaderApplication.ApplicationStatus.PENDING)")
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    ReaderApplication toEntity(ApplyReaderRequest request);

    /**
     * Maps a {@link ReaderApplication} entity to a {@link ReaderApplicationResponse} DTO.
     */
    @Mapping(source = "id", target = "id", qualifiedByName = "uuidToString")
    @Mapping(source = "status", target = "status", qualifiedByName = "enumToString")
    @Mapping(source = "createdAt", target = "createdAt")
    ReaderApplicationResponse toResponse(ReaderApplication application);

    // Helper methods for UUID to String and Enum to String conversions
    @Named("uuidToString")
    default String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    @Named("enumToString")
    default String enumToString(Object enumValue) {
        return enumValue != null ? enumValue.toString() : null;
    }
}
