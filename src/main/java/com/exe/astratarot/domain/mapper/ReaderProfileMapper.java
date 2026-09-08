package com.exe.astratarot.domain.mapper;

import com.exe.astratarot.domain.dto.reader.ReaderProfileResponse;
import com.exe.astratarot.domain.dto.reader.UpdateProfileRequest;
import com.exe.astratarot.domain.entity.ReaderProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper for the {@link ReaderProfile} entity and its DTOs.
 */
@Mapper(config = MapStructConfig.class)
public interface ReaderProfileMapper {

    /**
     * Maps a {@link ReaderProfile} entity to a {@link ReaderProfileResponse} DTO.
     */
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "user.id", target = "id")
    @Mapping(source = "available", target = "isAvailable")
    ReaderProfileResponse toResponse(ReaderProfile readerProfile);

    /**
     * Updates a {@link ReaderProfile} entity from an {@link UpdateProfileRequest} DTO.
     * Only non-null fields are updated (partial update).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "rating", ignore = true)
    @Mapping(target = "totalReviews", ignore = true)
    @Mapping(target = "verifiedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "available", ignore = true)
    void updateFromRequest(UpdateProfileRequest request, @MappingTarget ReaderProfile entity);
}
