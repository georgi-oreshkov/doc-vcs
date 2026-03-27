package com.root.vcsbackend.document.service;

import com.root.vcsbackend.document.domain.CategoryEntity;
import com.root.vcsbackend.document.domain.DocumentEntity;
import com.root.vcsbackend.document.mapper.DocumentMapper;
import com.root.vcsbackend.document.persistence.CategoryRepository;
import com.root.vcsbackend.document.persistence.DocumentRepository;
import com.root.vcsbackend.document.persistence.DocumentSpec;
import com.root.vcsbackend.model.CreateCategoryRequest;
import com.root.vcsbackend.model.CreateDocumentRequest;
import com.root.vcsbackend.model.S3UploadResponse;
import com.root.vcsbackend.shared.exception.AppException;
import com.root.vcsbackend.shared.s3.S3PresignService;
import com.root.vcsbackend.version.api.VersionFacade;
import com.root.vcsbackend.version.api.VersionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CategoryRepository categoryRepository;
    private final S3PresignService s3PresignService;
    private final ApplicationEventPublisher events;
    private final DocumentMapper documentMapper;
    private final VersionFacade versionFacade;

    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'AUTHOR', 'ADMIN')")
    public S3UploadResponse createDocument(UUID orgId, CreateDocumentRequest req, UUID callerId) {
        DocumentEntity doc = documentMapper.toEntity(req, orgId, callerId);
        doc = documentRepository.save(doc);

        VersionSummary initial = versionFacade.createInitialVersion(doc.getId());
        doc.setLatestVersionId(initial.id());
        documentRepository.save(doc);

        String uploadUrl = s3PresignService.generateUploadUrl(initial.s3Key());
        return new S3UploadResponse().uploadUrl(java.net.URI.create(uploadUrl));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public DocumentEntity getDocument(UUID docId) {
        return documentRepository.findById(docId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found: " + docId));
    }

    @Transactional(readOnly = true)
    public List<DocumentEntity> getMyDocuments(UUID authorId) {
        return documentRepository.findByAuthorId(authorId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN', 'AUTHOR', 'REVIEWER', 'READER')")
    public Page<DocumentEntity> listDocuments(UUID orgId, UUID categoryId, UUID authorId,
                                               String status, String name, int page, int size) {
        Specification<DocumentEntity> spec = Specification.where(DocumentSpec.hasOrgId(orgId));
        if (categoryId != null) spec = spec.and(DocumentSpec.hasCategoryId(categoryId));
        if (authorId != null)   spec = spec.and(DocumentSpec.hasAuthorId(authorId));
        if (status != null && !status.isBlank()) {
            try { spec = spec.and(DocumentSpec.hasStatus(status)); }
            catch (IllegalArgumentException e) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Invalid status value: " + status);
            }
        }
        if (name != null && !name.isBlank()) spec = spec.and(DocumentSpec.nameLike(name));
        return documentRepository.findAll(spec, PageRequest.of(page, size));
    }

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public DocumentEntity updateDocument(UUID docId, CreateDocumentRequest req) {
        DocumentEntity doc = getDocumentInternal(docId);
        documentMapper.applyUpdate(req, doc);
        return documentRepository.save(doc);
    }

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public void deleteDocument(UUID docId) {
        DocumentEntity doc = getDocumentInternal(docId);
        documentRepository.delete(doc);
    }

    // ── Categories ──────────────────────────────────────────────────────────

    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN', 'AUTHOR')")
    public CategoryEntity createCategory(UUID orgId, CreateCategoryRequest req) {
        CategoryEntity entity = documentMapper.toCategoryEntity(req, orgId);
        return categoryRepository.save(entity);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN', 'AUTHOR', 'REVIEWER', 'READER')")
    public List<CategoryEntity> listCategories(UUID orgId) {
        return categoryRepository.findByOrgId(orgId);
    }

    @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'ADMIN')")
    public void deleteCategory(UUID orgId, UUID catId) {
        CategoryEntity cat = categoryRepository.findById(catId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Category not found: " + catId));
        if (!cat.getOrgId().equals(orgId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Category not found in this organization");
        }
        if (documentRepository.existsByCategoryId(catId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Category still has documents");
        }
        categoryRepository.delete(cat);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private DocumentEntity getDocumentInternal(UUID docId) {
        return documentRepository.findById(docId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found: " + docId));
    }
}
