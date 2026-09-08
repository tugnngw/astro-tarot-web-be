package com.exe.astratarot.domain.mapper;

import com.exe.astratarot.domain.dto.auth.RegisterRequest;
import com.exe.astratarot.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

/**
 * Domain entity mapper for User.
 */
@Mapper(config = MapStructConfig.class)
public interface UserMapper {

    /**
     * Create a new User from a RegisterRequest.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", expression = "java(com.exe.astratarot.domain.enums.UserStatus.PENDING)")
    @Mapping(target = "role", expression = "java(com.exe.astratarot.domain.enums.UserRole.USER)")
    User toEntity(RegisterRequest request);

    /**
     * Update an existing user entity from a RegisterRequest.
     * Useful for profile updates while preserving identity fields.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromRequest(RegisterRequest request, @MappingTarget User entity);
}
