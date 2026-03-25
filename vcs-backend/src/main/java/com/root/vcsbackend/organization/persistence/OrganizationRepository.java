package com.root.vcsbackend.organization.persistence;

import com.root.vcsbackend.organization.domain.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {
}

