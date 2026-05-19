package com.project.cqrs.command.auth.infra.security;

import com.project.cqrs.command.auth.infra.cookie.CookieTokenUtil;
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

    public JwtAuthFilter(JwtTokenService jwtTokenService, CookieTokenUtil cookieTokenUtil) {
        this.jwtTokenService = jwtTokenService;
        this.cookieTokenUtil = cookieTokenUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {


        cookieTokenUtil.readToken(request)
                .flatMap(jwtTokenService::parseToken)
                .ifPresent(claims -> {
                    var auth = new UsernamePasswordAuthenticationToken(claims.getSubject(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + claims.get("role"))));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
        filterChain.doFilter(request,response);
    }
}
