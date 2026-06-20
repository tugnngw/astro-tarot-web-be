package com.exe.astratarot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // ✅ Log request path
        log.info("🔍 Processing request: {} {}", request.getMethod(), request.getRequestURI());

        try {
            final String authHeader = request.getHeader("Authorization");

            log.info("🔑 Auth header: {}", authHeader != null ? "Present" : "Missing");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.info("❌ No Bearer token found, continuing filter chain");
                filterChain.doFilter(request, response);
                return;
            }

            final String jwt = authHeader.substring(7);
            log.info("🔑 JWT token: {}...", jwt.substring(0, Math.min(jwt.length(), 30)));

            try {
                final String username = jwtService.extractUsername(jwt);
                log.info("👤 Extracted username: {}", username);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    log.info("👤 Loaded user details: {}", userDetails.getUsername());

                    if (jwtService.isTokenValid(jwt, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        log.info("✅ Authentication set for user: {}", username);
                    } else {
                        log.warn("⚠️ Invalid JWT token for user: {}", username);
                    }
                } else {
                    log.warn("⚠️ Username null or authentication already exists");
                }
            } catch (Exception e) {
                log.error("❌ Error processing JWT: {}", e.getMessage(), e);
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("❌ JWT authentication error: {}", e.getMessage(), e);
            filterChain.doFilter(request, response);
        }
    }
}