package com.root.vcsbackend.organization.service;

import com.root.vcsbackend.organization.persistence.OrgMembershipRepository;
import com.root.vcsbackend.organization.persistence.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrgMembershipRepository orgMembershipRepository;

    // TODO: implement org operations (create, getById, addMember, removeMember, etc.)
}

