package com.project.cqrs.command.auth.infra.security;

import com.project.cqrs.command.auth.dto.UserRequestDTO;
import com.project.cqrs.command.auth.infra.cookie.CookieTokenUtil;
import com.project.cqrs.command.auth.repository.UserCommandRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter  extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final CookieTokenUtil cookieTokenUtil;
    private final RefreshTokenService refreshTokenService;
    private final UserCommandRepository userCommandRepository;

    public JwtAuthFilter(JwtTokenService jwtTokenService, CookieTokenUtil cookieTokenUtil, RefreshTokenService refreshTokenService, UserCommandRepository userCommandRepository) {
        this.jwtTokenService = jwtTokenService;
        this.cookieTokenUtil = cookieTokenUtil;
        this.refreshTokenService = refreshTokenService;
        this.userCommandRepository = userCommandRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // Passo 1: tenta o access token (JWT)
        boolean authenticated = cookieTokenUtil.readToken(request)
                .flatMap(jwtTokenService::parseToken)
                .map(claims -> {
                    setAuthentication(request, claims.getSubject(),
                            (String) claims.get("role"));
                    return true;
                }).orElse(false);

        // Passo 2: JWT ausente/expirado -> tenta renovação silenciosa
        if (!authenticated) {
            tryRefresh(request, response);
        }

        filterChain.doFilter(request, response);
    }

    private void tryRefresh(HttpServletRequest request,
                            HttpServletResponse response) {

        cookieTokenUtil.readRefreshToken(request)
                .flatMap(refreshTokenService::validate)   // userId ou empty
                .flatMap(userId ->
                        userCommandRepository.findById(Long.parseLong(userId)))
                .ifPresent(user -> {
                    // Rotaciona: invalida refresh antigo, gera novo
                    String oldRefresh = cookieTokenUtil
                            .readRefreshToken(request).orElse("");
                    String newRefresh = refreshTokenService
                            .rotate(oldRefresh, user.getUserId().toString());

                    // Gera novo JWT
                    String newJwt = jwtTokenService
                            .generateToken(UserRequestDTO.from(user));

                    // Atualiza os dois cookies na resposta
                    cookieTokenUtil.writeToken(response, newJwt);
                    cookieTokenUtil.writeRefreshToken(response, newRefresh);

                    // Autentica a requisição atual
                    setAuthentication(request,
                            user.getUserId().toString(),
                            user.getUserRole().name());

                    logger.debug("Sessão renovada silenciosamente para userId="
                            + user.getUserId());
                });
    }

    private void setAuthentication(HttpServletRequest request, String userId, String role) {

        var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
