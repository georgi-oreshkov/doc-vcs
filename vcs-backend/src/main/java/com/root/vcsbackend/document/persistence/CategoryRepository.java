package com.root.vcsbackend.document.persistence;

import com.root.vcsbackend.document.domain.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findByOrgId(UUID orgId);
}

