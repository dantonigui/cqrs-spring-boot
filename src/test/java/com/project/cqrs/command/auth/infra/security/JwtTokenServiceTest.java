package com.project.cqrs.command.auth.infra.security;

import com.project.cqrs.command.auth.dto.UserRequestDTO;
import com.project.cqrs.command.auth.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do JwtTokenService — geração e validação do access token.
 *
 * Usa um secret real de 32 bytes (não mockado) para testar a
 * criptografia de ponta a ponta: gera um token, tenta decodificar,
 * confere claims, testa expiração e adulteração.
 */
@DisplayName("JwtTokenService")
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    // Secret de teste — 32 bytes em Base64, mesmo padrão do openssl rand -base64 32
    private static final String SECRET =
            "dGVzdC1qd3Qtc2VjcmV0LWNoYXZlLTMyLWJ5dGVzLW9r";
    private static final long   EXPIRATION_MS = 900_000L; // 15 minutos

    private static final Long   USER_ID    = 42L;
    private static final String USER_EMAIL = "usuario@teste.com";
    private static final UserRole USER_ROLE = UserRole.USER;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET, EXPIRATION_MS);
    }

    private UserRequestDTO buildUserDto() {
        return new UserRequestDTO(USER_ID, USER_EMAIL, USER_ROLE);
    }

    // ── generateToken ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateToken()")
    class GenerateToken {

        @Test
        @DisplayName("deve gerar um token não nulo e não vazio")
        void shouldGenerateNonEmptyToken() {
            String token = jwtTokenService.generateToken(buildUserDto());
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("token gerado deve ter 3 partes separadas por ponto (header.payload.signature)")
        void shouldHaveThreeParts() {
            String token = jwtTokenService.generateToken(buildUserDto());
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("deve incluir userId como subject")
        void shouldIncludeUserIdAsSubject() {
            String token = jwtTokenService.generateToken(buildUserDto());

            Claims claims = parseWithoutValidation(token);
            assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        }

        @Test
        @DisplayName("deve incluir email e role como claims customizados")
        void shouldIncludeEmailAndRoleClaims() {
            String token = jwtTokenService.generateToken(buildUserDto());

            Claims claims = parseWithoutValidation(token);
            assertThat(claims.get(JwtTokenService.CLAIM_EMAIL)).isEqualTo(USER_EMAIL);
            assertThat(claims.get(JwtTokenService.CLAIM_ROLE)).isEqualTo(USER_ROLE.name());
        }

        @Test
        @DisplayName("deve definir expiração no futuro, respeitando o TTL configurado")
        void shouldSetExpirationInFuture() {
            String token = jwtTokenService.generateToken(buildUserDto());

            Claims claims = parseWithoutValidation(token);
            long expiresInMs = claims.getExpiration().getTime()
                    - claims.getIssuedAt().getTime();

            // Tolerância de 1s para diferença de processamento
            assertThat(expiresInMs).isCloseTo(EXPIRATION_MS, within(1000L));
        }

        // Helper local para evitar import estático confuso com Assertions.within
        private org.assertj.core.data.Offset<Long> within(long value) {
            return org.assertj.core.data.Offset.offset(value);
        }
    }

    // ── parseToken — caminho feliz ────────────────────────────────────────────

    @Nested
    @DisplayName("parseToken() — token válido")
    class ParseValidToken {

        @Test
        @DisplayName("deve retornar Optional presente para token recém-gerado")
        void shouldReturnPresentForFreshToken() {
            String token = jwtTokenService.generateToken(buildUserDto());

            Optional<Claims> result = jwtTokenService.parseToken(token);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("claims retornados devem bater com os dados originais")
        void claimsShouldMatchOriginalData() {
            String token = jwtTokenService.generateToken(buildUserDto());

            Claims claims = jwtTokenService.parseToken(token).orElseThrow();

            assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
            assertThat(claims.get(JwtTokenService.CLAIM_EMAIL)).isEqualTo(USER_EMAIL);
        }
    }

    // ── parseToken — cenários de falha ────────────────────────────────────────

    @Nested
    @DisplayName("parseToken() — token inválido")
    class ParseInvalidToken {

        @Test
        @DisplayName("deve retornar Optional vazio para token nulo")
        void shouldReturnEmptyForNullToken() {
            assertThat(jwtTokenService.parseToken(null)).isEmpty();
        }

        @Test
        @DisplayName("deve retornar Optional vazio para string vazia")
        void shouldReturnEmptyForBlankToken() {
            assertThat(jwtTokenService.parseToken("")).isEmpty();
        }

        @Test
        @DisplayName("deve retornar Optional vazio para token malformado")
        void shouldReturnEmptyForMalformedToken() {
            assertThat(jwtTokenService.parseToken("isso.nao.eh-jwt-valido"))
                    .isEmpty();
        }

        @Test
        @DisplayName("deve retornar Optional vazio para token assinado com secret diferente")
        void shouldReturnEmptyForTokenSignedWithDifferentSecret() {
            SecretKey outroSecret = Keys.hmacShaKeyFor(
                    Base64.getDecoder().decode(
                            "b3V0cm8tc2VjcmV0LWRpZmVyZW50ZS0zMi1ieXRlcyE="));

            String tokenComOutraAssinatura = Jwts.builder()
                    .issuer("auth-service")
                    .subject(USER_ID.toString())
                    .signWith(outroSecret)
                    .compact();

            assertThat(jwtTokenService.parseToken(tokenComOutraAssinatura))
                    .isEmpty();
        }

        @Test
        @DisplayName("deve retornar Optional vazio para token com issuer diferente")
        void shouldReturnEmptyForTokenWithWrongIssuer() {
            SecretKey mesmoSecret = Keys.hmacShaKeyFor(
                    Base64.getDecoder().decode(SECRET));

            String tokenComOutroIssuer = Jwts.builder()
                    .issuer("outro-servico-qualquer")
                    .subject(USER_ID.toString())
                    .signWith(mesmoSecret)
                    .compact();

            // requireIssuer("auth-service") deve rejeitar
            assertThat(jwtTokenService.parseToken(tokenComOutroIssuer))
                    .isEmpty();
        }

        @Test
        @DisplayName("deve retornar Optional vazio para token já expirado")
        void shouldReturnEmptyForExpiredToken() {
            // Instancia um service com TTL negativo — token já nasce expirado
            JwtTokenService expiredTokenService =
                    new JwtTokenService(SECRET, -1000L);

            String expiredToken = expiredTokenService.generateToken(buildUserDto());

            assertThat(jwtTokenService.parseToken(expiredToken)).isEmpty();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Decodifica o token sem validar assinatura/issuer — usado apenas
     * para inspecionar claims nos testes de geração, onde queremos
     * confirmar o conteúdo sem depender do próprio parseToken().
     */
    private Claims parseWithoutValidation(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}