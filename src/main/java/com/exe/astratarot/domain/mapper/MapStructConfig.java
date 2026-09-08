package com.exe.astratarot.domain.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Shared MapStruct configuration for all mappers.
 *
 * - componentModel = "spring" enables Spring bean injection.
 * - unmappedTargetPolicy = ReportingPolicy.IGNORE skips unmapped target warnings.
 * - unmappedSourcePolicy = ReportingPolicy.WARN to surface potential omissions.
 */
@MapperConfig(componentModel = "spring",
              unmappedTargetPolicy = ReportingPolicy.IGNORE,
              unmappedSourcePolicy = ReportingPolicy.WARN)
public interface MapStructConfig {
    // Marker interface – no methods needed.
}
