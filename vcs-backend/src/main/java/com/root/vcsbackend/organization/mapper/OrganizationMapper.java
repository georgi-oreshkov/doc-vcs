package com.root.vcsbackend.organization.mapper;

import com.root.vcsbackend.model.CreateOrganizationRequest;
import com.root.vcsbackend.model.OrgUser;
import com.root.vcsbackend.model.Organization;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity.OrgRole;
import com.root.vcsbackend.organization.domain.OrganizationEntity;
import com.root.vcsbackend.shared.mapper.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.UUID;

@Mapper(componentModel = "spring", config = MapStructConfig.class)
public interface OrganizationMapper {

    Organization toDto(OrganizationEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    OrganizationEntity toEntity(CreateOrganizationRequest req);

    @Mapping(target = "userId", source = "membership.userId")
    @Mapping(target = "role", source = "membership.role", qualifiedByName = "orgRoleToApi")
    OrgUser toOrgUserDto(OrgMembershipEntity membership);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "userId", source = "req.userId")
    @Mapping(target = "role", source = "req.role", qualifiedByName = "apiRoleToOrgRole")
    OrgMembershipEntity toMembershipEntity(OrgUser req, UUID orgId);

    @Named("orgRoleToApi")
    default OrgUser.RoleEnum orgRoleToApi(OrgRole role) {
        return OrgUser.RoleEnum.fromValue(role.name());
    }

    @Named("apiRoleToOrgRole")
    default OrgRole apiRoleToOrgRole(OrgUser.RoleEnum roleEnum) {
        return OrgRole.valueOf(roleEnum.getValue());
    }
}
