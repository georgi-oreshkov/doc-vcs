package com.root.vcsbackend.document.mapper;

import com.root.vcsbackend.document.domain.CategoryEntity;
import com.root.vcsbackend.document.domain.DocumentEntity;
import com.root.vcsbackend.document.domain.DocumentEntity.DocumentStatus;
import com.root.vcsbackend.model.Category;
import com.root.vcsbackend.model.CreateCategoryRequest;
import com.root.vcsbackend.model.CreateDocumentRequest;
import com.root.vcsbackend.model.Document;
import com.root.vcsbackend.model.Document.StatusEnum;
import com.root.vcsbackend.shared.mapper.JsonNullableMapper;
import com.root.vcsbackend.shared.mapper.MapStructConfig;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.ArrayList;
import java.util.UUID;

@Mapper(componentModel = "spring", config = MapStructConfig.class, uses = JsonNullableMapper.class)
public interface DocumentMapper {

    @Mapping(target = "status", source = "status", qualifiedByName = "docStatusToApi")
    @Mapping(target = "categoryId", source = "categoryId", qualifiedByName = "uuidToJsonNullable")
    @Mapping(target = "latestVersionId", source = "latestVersionId", qualifiedByName = "uuidToJsonNullable")
    @Mapping(target = "latestApprovedVersionId", source = "latestApprovedVersionId", qualifiedByName = "uuidToJsonNullable")
    Document toDto(DocumentEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "authorId", source = "authorId")
    @Mapping(target = "name", source = "req.name")
    @Mapping(target = "categoryId", source = "req.categoryId")
    @Mapping(target = "reviewerIds", source = "req.reviewerIds")
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "latestVersionId", ignore = true)
    @Mapping(target = "latestApprovedVersionId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    DocumentEntity toEntity(CreateDocumentRequest req, UUID orgId, UUID authorId);

    @AfterMapping
    default void initReviewerIds(CreateDocumentRequest req, @MappingTarget DocumentEntity entity) {
        if (entity.getReviewerIds() == null) {
            entity.setReviewerIds(new ArrayList<>());
        }
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orgId", ignore = true)
    @Mapping(target = "authorId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "latestVersionId", ignore = true)
    @Mapping(target = "latestApprovedVersionId", ignore = true)
    @Mapping(target = "reviewerIds", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void applyUpdate(CreateDocumentRequest req, @MappingTarget DocumentEntity entity);

    Category toCategoryDto(CategoryEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "name", source = "req.name")
    CategoryEntity toCategoryEntity(CreateCategoryRequest req, UUID orgId);

    @Named("docStatusToApi")
    default StatusEnum docStatusToApi(DocumentStatus status) {
        return StatusEnum.fromValue(status.name());
    }
}
