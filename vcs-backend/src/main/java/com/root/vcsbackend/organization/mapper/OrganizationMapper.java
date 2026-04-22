package com.root.vcsbackend.organization.mapper;

import com.root.vcsbackend.model.CreateOrganizationRequest;
import com.root.vcsbackend.model.OrgUser;
import com.root.vcsbackend.model.Organization;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity;
import com.root.vcsbackend.organization.domain.OrgMembershipEntity.OrgRole;
import com.root.vcsbackend.organization.domain.OrgUserRoleEntity;
import com.root.vcsbackend.organization.domain.OrganizationEntity;
import com.root.vcsbackend.shared.mapper.MapStructConfig;
import com.root.vcsbackend.user.domain.UserProfileEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", config = MapStructConfig.class)
public interface OrganizationMapper {

    Organization toDto(OrganizationEntity entity);

    default Organization toDto(OrganizationEntity entity, List<OrgUserRoleEntity> roles) {
        Organization dto = toDto(entity);
        List<Organization.MyRolesEnum> apiRoles = roles.stream()
            .map(r -> Organization.MyRolesEnum.fromValue(r.getRole().name()))
            .toList();
        dto.setMyRoles(apiRoles);
        return dto;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    OrganizationEntity toEntity(CreateOrganizationRequest req);

    default OrgUser toOrgUserDto(OrgMembershipEntity membership, List<OrgUserRoleEntity> roles, UserProfileEntity profile) {
        OrgUser dto = new OrgUser();
        dto.setUserId(membership.getUserId());
        dto.setRoles(roles.stream()
            .map(r -> OrgUser.RolesEnum.fromValue(r.getRole().name()))
            .toList());
        if (profile != null) {
            dto.setName(profile.getName());
            dto.setEmail(profile.getEmail());
        }
        return dto;
    }

    default OrgRole apiRoleToOrgRole(OrgUser.RolesEnum roleEnum) {
        return OrgRole.valueOf(roleEnum.getValue());
    }
}
