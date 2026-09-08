package com.exe.astratarot.security;

import com.exe.astratarot.exception.InvalidTokenException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails, Map.of());
    }

    public String generateToken(UserDetails userDetails, Map<String, Object> claims) {
        String subject = userDetails instanceof CustomUserDetails customUserDetails
                ? customUserDetails.getUser().getId().toString()
                : userDetails.getUsername();

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String generateTokenFromEmail(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Long getExpiration() {
        return expiration;
    }

    public String extractSubject(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String extractEmail(String token) {
        return extractSubject(token);
    }

    public String extractSubjectSafely(String token) {
        try {
            return extractSubject(token);
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("JWT token expired");
        } catch (MalformedJwtException e) {
            throw new InvalidTokenException("JWT token malformed");
        } catch (UnsupportedJwtException e) {
            throw new InvalidTokenException("JWT token unsupported");
        } catch (SignatureException e) {
            throw new InvalidTokenException("JWT signature invalid");
        } catch (JwtException e) {
            throw new InvalidTokenException("JWT token invalid");
        }
    }

    public String extractEmailSafely(String token) {
        return extractSubjectSafely(token);
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        String subject = extractSubject(token);
        if (userDetails instanceof CustomUserDetails customUserDetails) {
            return subject.equals(customUserDetails.getUser().getId().toString()) && !isTokenExpired(token);
        }
        return subject.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }
}
