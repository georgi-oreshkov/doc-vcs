package com.root.vcsbackend.document.web;

import com.root.vcsbackend.api.MetadataApi;
import com.root.vcsbackend.document.service.DocumentService;
import com.root.vcsbackend.model.Category;
import com.root.vcsbackend.model.CreateCategoryRequest;
import com.root.vcsbackend.shared.security.CurrentUser;
import com.root.vcsbackend.shared.security.JwtPrincipal;
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

    @Override
    public ResponseEntity<Category> createCategory(UUID orgId, CreateCategoryRequest createCategoryRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> deleteCategory(UUID orgId, UUID catId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<Category>> listCategories(UUID orgId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
