package com.root.vcsbackend.document.web;

import com.root.vcsbackend.api.MetadataApi;
import com.root.vcsbackend.document.mapper.DocumentMapper;
import com.root.vcsbackend.document.service.DocumentService;
import com.root.vcsbackend.model.Category;
import com.root.vcsbackend.model.CreateCategoryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MetadataController implements MetadataApi {

    private final DocumentService documentService;
    private final DocumentMapper documentMapper;

    @Override
    public ResponseEntity<Category> createCategory(UUID orgId, CreateCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(documentMapper.toCategoryDto(documentService.createCategory(orgId, req)));
    }

    @Override
    public ResponseEntity<Void> deleteCategory(UUID orgId, UUID catId) {
        documentService.deleteCategory(orgId, catId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<Category>> listCategories(UUID orgId) {
        List<Category> cats = documentService.listCategories(orgId).stream()
            .map(documentMapper::toCategoryDto)
            .toList();
        return ResponseEntity.ok(cats);
    }
}
