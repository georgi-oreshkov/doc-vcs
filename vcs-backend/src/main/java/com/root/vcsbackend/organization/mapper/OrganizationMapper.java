package com.root.vcsbackend.organization.mapper;

import com.root.vcsbackend.model.CreateOrganizationRequest;
import com.root.vcsbackend.model.OrgUser;
import com.root.vcsbackend.model.Organization;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity.OrgRole;
import com.root.vcsbackend.organization.domain.OrganizationEntity;
import com.root.vcsbackend.shared.mapper.MapStructConfig;
import com.root.vcsbackend.user.domain.UserProfileEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.UUID;

@Mapper(componentModel = "spring", config = MapStructConfig.class)
public interface OrganizationMapper {

    Organization toDto(OrganizationEntity entity);

    default Organization toDto(OrganizationEntity entity, OrgRole role) {
        Organization dto = toDto(entity);
        dto.setMyRole(orgRoleToMyRole(role));
        return dto;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    OrganizationEntity toEntity(CreateOrganizationRequest req);

    @Mapping(target = "userId", source = "membership.userId")
    @Mapping(target = "role", source = "membership.role", qualifiedByName = "orgRoleToApi")
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "email", ignore = true)
    OrgUser toOrgUserDto(OrgMembershipEntity membership);

    default OrgUser toOrgUserDto(OrgMembershipEntity membership, UserProfileEntity profile) {
        OrgUser dto = toOrgUserDto(membership);
        if (profile != null) {
            dto.setName(profile.getName());
            dto.setEmail(profile.getEmail());
        }
        return dto;
    }

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

    default Organization.MyRoleEnum orgRoleToMyRole(OrgRole role) {
        return Organization.MyRoleEnum.fromValue(role.name());
    }
}
