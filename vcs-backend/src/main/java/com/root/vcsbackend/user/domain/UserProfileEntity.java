package com.root.vcsbackend.user.domain;

import com.root.vcsbackend.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@Builder
public class UserProfileEntity extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id; // mirrors Keycloak subject (sub)

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String photoUrl;
}

