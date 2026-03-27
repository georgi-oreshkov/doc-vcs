package com.root.vcsbackend.shared.mapper;

import org.mapstruct.Builder;
import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Global MapStruct configuration shared by all @Mapper interfaces.
 * disableBuilder = true: MapStruct uses the no-arg constructor + setters for entity
 * construction instead of Lombok's @Builder. This is required because Lombok's @Builder
 * only generates builder methods for fields declared in the current class, NOT for
 * inherited fields (e.g. createdAt/updatedAt/createdBy from BaseEntity).
 * Service code continues to use Entity.builder()...build() directly.
 */
@MapperConfig(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    builder = @Builder(disableBuilder = true)
)
public interface MapStructConfig {
}

