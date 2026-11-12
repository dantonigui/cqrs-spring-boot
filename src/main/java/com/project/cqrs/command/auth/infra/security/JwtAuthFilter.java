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
public class JwtAuthFilter extends OncePerRequestFilter {
    public static final String SESSION_ATTRIBUTE = JwtAuthFilter.class.getName() + ".sessionId";
    private final JwtTokenService jwtTokenService;
    private final CookieTokenUtil cookieTokenUtil;
    private final RefreshTokenService refreshTokenService;
    private final UserCommandRepository userCommandRepository;

    public JwtAuthFilter(JwtTokenService jwtTokenService, CookieTokenUtil cookieTokenUtil,
                         RefreshTokenService refreshTokenService, UserCommandRepository userCommandRepository) {
        this.jwtTokenService = jwtTokenService;
        this.cookieTokenUtil = cookieTokenUtil;
        this.refreshTokenService = refreshTokenService;
        this.userCommandRepository = userCommandRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean authenticated = cookieTokenUtil.readToken(request)
                .flatMap(jwtTokenService::parseToken)
                .filter(claims -> refreshTokenService.isSessionActive(
                        claims.get("sid", String.class), claims.getSubject()))
                .map(claims -> {
                    setAuthentication(request, claims.getSubject(), claims.get("role", String.class),
                            claims.get("sid", String.class));
                    return true;
                }).orElse(false);
        if (!authenticated) tryRefresh(request, response);
        filterChain.doFilter(request, response);
    }

    private void tryRefresh(HttpServletRequest request, HttpServletResponse response) {
        // Logout must authenticate an expired access token without issuing replacement tokens.
        boolean logout = "POST".equals(request.getMethod()) &&
                ("/api/v1/command/auth/logout".equals(request.getServletPath()) ||
                 "/api/v1/command/auth/logout-all".equals(request.getServletPath()));
        cookieTokenUtil.readRefreshToken(request)
                .flatMap(refreshTokenService::lookup)
                .ifPresent(session -> userCommandRepository.findById(Long.parseLong(session.userId()))
                        .ifPresent(user -> {
                            if (logout) {
                                setAuthentication(request, session.userId(), user.getUserRole().name(), session.sessionId());
                                return;
                            }
                            refreshTokenService.rotate(session.refreshToken()).ifPresent(rotated -> {
                                String jwt = jwtTokenService.generateToken(UserRequestDTO.from(user), rotated.sessionId());
                                cookieTokenUtil.writeToken(response, jwt);
                                cookieTokenUtil.writeRefreshToken(response, rotated.refreshToken());
                                setAuthentication(request, rotated.userId(), user.getUserRole().name(), rotated.sessionId());
                            });
                        }));
    }

    private void setAuthentication(HttpServletRequest request, String userId, String role, String sessionId) {
        var auth = new UsernamePasswordAuthenticationToken(userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        request.setAttribute(SESSION_ATTRIBUTE, sessionId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
