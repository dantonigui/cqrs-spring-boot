package com.project.cqrs.command.auth.infra.security;

import com.project.cqrs.command.auth.model.UserCommandEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long expirationTime;

    private static final String ISSUER = "auth-service";

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE  = "role";

    public JwtTokenService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-ms}") long expirationTime) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationTime = expirationTime;
    }

    public String generateToken(UserCommandEntity userCommandEntity) {
        Instant now = Instant.now();

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userCommandEntity.getUserId().toString())
                .claim(CLAIM_EMAIL, userCommandEntity.getUserEmail())
                .claim(CLAIM_ROLE, userCommandEntity.getUserRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationTime)))
                .signWith(secretKey)
                .compact();
    }

    public Optional<Claims> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(claims);
        } catch (JwtException e) {
            log.warn("Token JWT inválido: {}", e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            log.warn("Token JWT malformado ou ausente: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
