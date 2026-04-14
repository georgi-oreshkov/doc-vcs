package com.root.vcsbackend.version.mapper;

import com.root.vcsbackend.model.AddCommentRequest;
import com.root.vcsbackend.model.Comment;
import com.root.vcsbackend.model.CreateVersionRequest;
import com.root.vcsbackend.model.Version;
import com.root.vcsbackend.model.Version.StatusEnum;
import com.root.vcsbackend.shared.mapper.JsonNullableMapper;
import com.root.vcsbackend.shared.mapper.MapStructConfig;
import com.root.vcsbackend.version.domain.CommentEntity;
import com.root.vcsbackend.version.domain.VersionEntity;
import com.root.vcsbackend.version.domain.VersionStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.UUID;

@Mapper(componentModel = "spring", config = MapStructConfig.class, uses = JsonNullableMapper.class)
public interface VersionMapper {

    @Mapping(target = "status", source = "status", qualifiedByName = "versionStatusToApi")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "instantToOffsetDateTime")
    Version toDto(VersionEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "docId", source = "docId")
    @Mapping(target = "versionNumber", source = "nextVersionNumber")
    @Mapping(target = "isDraft", source = "req.isDraft")
    @Mapping(target = "checksum", source = "req.checksum")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "storageType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    VersionEntity toEntity(CreateVersionRequest req, UUID docId, int nextVersionNumber);

    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "instantToOffsetDateTime")
    Comment toCommentDto(CommentEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "versionId", source = "versionId")
    @Mapping(target = "authorId", source = "authorId")
    @Mapping(target = "content", source = "req.content")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    CommentEntity toCommentEntity(AddCommentRequest req, UUID versionId, UUID authorId);

    @Named("versionStatusToApi")
    default StatusEnum versionStatusToApi(VersionStatus status) {
        return StatusEnum.fromValue(status.name());
    }
}
