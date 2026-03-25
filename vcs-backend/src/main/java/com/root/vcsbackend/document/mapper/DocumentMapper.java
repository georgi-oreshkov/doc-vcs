package com.root.vcsbackend.document.mapper;

import com.root.vcsbackend.document.domain.CategoryEntity;
import com.root.vcsbackend.document.domain.DocumentEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DocumentMapper {

    // TODO: import and map to/from generated API model DTOs

    public Object toDto(DocumentEntity entity) {
        // TODO: implement
        return null;
    }

    public DocumentEntity toEntity(Object createRequest, UUID orgId, UUID authorId) {
        // TODO: implement
        return null;
    }

    public Object toCategoryDto(CategoryEntity entity) {
        // TODO: implement
        return null;
    }
}

