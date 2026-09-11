package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {
}
