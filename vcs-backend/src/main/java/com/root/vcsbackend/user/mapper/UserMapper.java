package com.root.vcsbackend.user.mapper;

import com.root.vcsbackend.model.UpdateUserProfileRequest;
import com.root.vcsbackend.model.UserProfile;
import com.root.vcsbackend.shared.mapper.JsonNullableMapper;
import com.root.vcsbackend.shared.mapper.MapStructConfig;
import com.root.vcsbackend.user.domain.UserProfileEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.openapitools.jackson.nullable.JsonNullable;

import java.net.URI;

@Mapper(componentModel = "spring", config = MapStructConfig.class, uses = JsonNullableMapper.class)
public interface UserMapper {

    @Mapping(target = "photoUrl", source = "photoUrl", qualifiedByName = "stringToJsonNullableUri")
    UserProfile toDto(UserProfileEntity entity);

    /** Patch: ignore null/undefined fields in the request. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "photoUrl", ignore = true) // handled in @AfterMapping due to JsonNullable<URI> → String
    void applyUpdate(UpdateUserProfileRequest req, @MappingTarget UserProfileEntity entity);

    @AfterMapping
    default void applyPhotoUrl(UpdateUserProfileRequest req, @MappingTarget UserProfileEntity entity) {
        JsonNullable<URI> photoUrl = req.getPhotoUrl();
        if (photoUrl != null && photoUrl.isPresent()) {
            entity.setPhotoUrl(photoUrl.get() != null ? photoUrl.get().toString() : null);
        }
    }
}
