package com.tss.aml.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateToken(Authentication authentication) {

        CustomUserDetails user =
                (CustomUserDetails) authentication.getPrincipal();

        Date now = new Date();

        Date expiryDate =
                new Date(now.getTime() + jwtExpiration);

        List<String> roles = user.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(user.getUserId().toString())

                .claim("roles", roles)

                .claim(
                        "tenantId",
                        user.getTenantId().toString()
                )

                .claim(
                        "tenantCode",
                        user.getTenantCode()
                )

                .claim(
                        "jti",
                        UUID.randomUUID().toString()
                )

                .issuedAt(now)
                .expiration(expiryDate)

                .signWith(
                        getSigningKey()
                )

                .compact();
    }

    public Claims getClaims(String token) {

        return Jwts.parser()
                .verifyWith(
                        (javax.crypto.SecretKey) getSigningKey()
                )
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(String token) {

        return UUID.fromString(
                getClaims(token).getSubject()
        );
    }

    public String getTenantCode(String token) {

        return getClaims(token)
                .get("tenantCode", String.class);
    }

    public UUID getTenantId(String token) {

        return UUID.fromString(
                getClaims(token)
                        .get("tenantId", String.class)
        );
    }

    public boolean validateToken(String token) {

        try {

            Jwts.parser()
                    .verifyWith(
                            (javax.crypto.SecretKey) getSigningKey()
                    )
                    .build()
                    .parseSignedClaims(token);

            return true;

        } catch (
                SecurityException |
                MalformedJwtException |
                ExpiredJwtException |
                UnsupportedJwtException |
                IllegalArgumentException e
        ) {

            return false;
        }
    }
}