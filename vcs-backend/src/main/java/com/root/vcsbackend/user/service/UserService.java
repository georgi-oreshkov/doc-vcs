package com.root.vcsbackend.user.service;

import com.root.vcsbackend.user.persistence.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;

    // TODO: implement user profile operations (getProfile, updateProfile, etc.)
}

