package com.root.vcsbackend.document.persistence;

import com.root.vcsbackend.document.domain.DocumentEntity;
import com.root.vcsbackend.document.domain.DocumentEntity.DocumentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/** JPA Specification factories for DocumentRepository filtered queries. */
public final class DocumentSpec {

    private DocumentSpec() {}

    public static Specification<DocumentEntity> hasOrgId(UUID orgId) {
        return (root, query, cb) -> cb.equal(root.get("orgId"), orgId);
    }

    public static Specification<DocumentEntity> hasCategoryId(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<DocumentEntity> hasAuthorId(UUID authorId) {
        return (root, query, cb) -> cb.equal(root.get("authorId"), authorId);
    }

    public static Specification<DocumentEntity> hasStatus(String status) {
        DocumentStatus s = DocumentStatus.valueOf(status.toUpperCase());
        return (root, query, cb) -> cb.equal(root.get("status"), s);
    }

    public static Specification<DocumentEntity> nameLike(String name) {
        return (root, query, cb) ->
            cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }
}

