package com.checador.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Long extractTenantId(String token) {
        Object tenantId = extractClaim(token, claims -> claims.get("tenantId"));
        if (tenantId != null) {
            if (tenantId instanceof Integer) {
                return ((Integer) tenantId).longValue();
            } else if (tenantId instanceof Long) {
                return (Long) tenantId;
            } else if (tenantId instanceof String) {
                return Long.parseLong((String) tenantId);
            }
        }
        return null;
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("tokenType", String.class));
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "ACCESS");
        if (userDetails instanceof com.checador.entity.User user) {
            claims.put("role", user.getRole().name());
            claims.put("fullName", user.getFullName());
            if (user.getTenantId() != null) {
                claims.put("tenantId", user.getTenantId());
            }
            if (user.getBranch() != null) {
                claims.put("branchId", user.getBranch().getId());
                claims.put("branchName", user.getBranch().getName());
            }
        }
        return buildToken(claims, userDetails, jwtExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "REFRESH");
        if (userDetails instanceof com.checador.entity.User user && user.getTenantId() != null) {
            claims.put("tenantId", user.getTenantId());
        }
        return buildToken(claims, userDetails, refreshExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        if (!username.equals(userDetails.getUsername()) || isTokenExpired(token)) {
            return false;
        }
        String tokenType = extractTokenType(token);
        if ("REFRESH".equalsIgnoreCase(tokenType)) {
            return false;
        }
        Long tokenTenantId = extractTenantId(token);
        if (tokenTenantId == null) {
            return false;
        }
        if (userDetails instanceof com.checador.entity.User user) {
            if (user.getTenantId() != null) {
                return tokenTenantId.equals(user.getTenantId());
            }
        }
        return true;
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        if (!username.equals(userDetails.getUsername()) || isTokenExpired(token)) {
            return false;
        }
        String tokenType = extractTokenType(token);
        if ("ACCESS".equalsIgnoreCase(tokenType)) {
            return false;
        }
        Long tokenTenantId = extractTenantId(token);
        if (tokenTenantId == null) {
            return false;
        }
        if (userDetails instanceof com.checador.entity.User user) {
            if (user.getTenantId() != null) {
                return tokenTenantId.equals(user.getTenantId());
            }
        }
        return true;
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
