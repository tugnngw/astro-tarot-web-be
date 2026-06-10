package com.exe.astratarot.security;

import com.exe.astratarot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            UUID userId = UUID.fromString(username);
            return userRepository.findById(userId)
                    .filter(user -> user.getDeletedAt() == null)
                    .map(CustomUserDetails::new)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        } catch (IllegalArgumentException ignored) {
            return userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(username)
                    .map(CustomUserDetails::new)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        }
    }
}
