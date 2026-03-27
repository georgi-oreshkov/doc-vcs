package com.root.vcsbackend.request.mapper;

import com.root.vcsbackend.model.CreateForkRequestRequest;
import com.root.vcsbackend.model.ForkRequest;
import com.root.vcsbackend.model.ForkRequest.StatusEnum;
import com.root.vcsbackend.request.domain.ForkRequestEntity;
import com.root.vcsbackend.request.domain.ForkRequestEntity.RequestStatus;
import com.root.vcsbackend.shared.mapper.JsonNullableMapper;
import com.root.vcsbackend.shared.mapper.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.UUID;

@Mapper(componentModel = "spring", config = MapStructConfig.class, uses = JsonNullableMapper.class)
public interface RequestMapper {

    @Mapping(target = "status", source = "status", qualifiedByName = "requestStatusToApi")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "instantToOffsetDateTime")
    ForkRequest toDto(ForkRequestEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "requesterId", source = "requesterId")
    @Mapping(target = "docId", source = "req.docId")
    @Mapping(target = "versionId", source = "req.versionId")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    ForkRequestEntity toEntity(CreateForkRequestRequest req, UUID requesterId);

    @Named("requestStatusToApi")
    default StatusEnum requestStatusToApi(RequestStatus status) {
        return StatusEnum.fromValue(status.name());
    }
}
