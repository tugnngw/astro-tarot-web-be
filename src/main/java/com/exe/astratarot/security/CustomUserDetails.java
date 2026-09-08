package com.exe.astratarot.security;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import com.exe.astratarot.domain.enums.UserRole;
import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        // Gắn role gốc (Backward compatibility)
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        
        // Gán Permissions tương ứng với từng Role
        if (user.getRole() == UserRole.ADMIN) {
            authorities.add(new SimpleGrantedAuthority(SecurityPermissions.ADMIN_READERS_VIEW));
            authorities.add(new SimpleGrantedAuthority(SecurityPermissions.ADMIN_READERS_REVIEW));
            authorities.add(new SimpleGrantedAuthority(SecurityPermissions.READER_MANAGE_PROFILE));
        } else if (user.getRole() == UserRole.READER) {
            authorities.add(new SimpleGrantedAuthority(SecurityPermissions.READER_APPLY));
            authorities.add(new SimpleGrantedAuthority(SecurityPermissions.READER_MANAGE_PROFILE));
            authorities.add(new SimpleGrantedAuthority(SecurityPermissions.USER_BASIC));
        } else if (user.getRole() == UserRole.USER) {
            authorities.add(new SimpleGrantedAuthority(SecurityPermissions.READER_APPLY));
            authorities.add(new SimpleGrantedAuthority(SecurityPermissions.USER_BASIC));
        }
        
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.BANNED && user.getDeletedAt() == null;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null;
    }
}
