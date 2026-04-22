package com.root.vcsbackend.document.persistence;

import com.root.vcsbackend.document.domain.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID>,
        JpaSpecificationExecutor<DocumentEntity> {

    Page<DocumentEntity> findByOrgId(UUID orgId, Pageable pageable);

    List<DocumentEntity> findByAuthorId(UUID authorId);

    boolean existsByCategoryId(UUID categoryId);

    @Query("SELECT d FROM DocumentEntity d WHERE :reviewerId MEMBER OF d.reviewerIds")
    List<DocumentEntity> findByReviewerContaining(@Param("reviewerId") UUID reviewerId);
}
