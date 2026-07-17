package com.project.cqrs.config.security;

import com.project.cqrs.command.auth.infra.cookie.CookieTokenUtil;
import com.project.cqrs.command.auth.infra.cookie.HttpCookieOAuth2AuthorizationRequestRepository;
import com.project.cqrs.command.auth.infra.security.JwtAuthFilter;
import com.project.cqrs.command.auth.infra.security.JwtTokenService;
import com.project.cqrs.command.auth.infra.security.OAuth2AuthSucessHandler;
import com.project.cqrs.command.auth.infra.security.RefreshTokenService;
import com.project.cqrs.command.auth.service.CustomOAuth2UserService;
import com.project.cqrs.command.auth.repository.UserCommandRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final JwtAuthFilter jwtAuthFilter;
    private final JwtTokenService jwtTokenService;
    private final UserCommandRepository userCommandRepository;
    private final CookieTokenUtil cookieTokenUtil;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          JwtAuthFilter jwtAuthFilter,
                          JwtTokenService jwtTokenService,
                          UserCommandRepository userCommandRepository,
                          CookieTokenUtil cookieTokenUtil,
                          RefreshTokenService refreshTokenService) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.jwtAuthFilter = jwtAuthFilter;
        this.jwtTokenService = jwtTokenService;
        this.userCommandRepository = userCommandRepository;
        this.cookieTokenUtil = cookieTokenUtil;
        this.refreshTokenService = refreshTokenService;
    }

    @Bean
    public HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository() {
        return new HttpCookieOAuth2AuthorizationRequestRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Stateless — JWT via cookie, sem sessão no servidor
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                                .sessionFixation().none())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String path = request.getRequestURI();

                            // Deixa o Spring lidar normalmente com o fluxo OAuth2
                            if (path.startsWith("/oauth2/")
                                    || path.startsWith("/login/oauth2/")
                                    || path.startsWith("/login")
                                    || path.equals("/favicon.ico")
                                    || path.equals("/error")) {
                                return;
                            }

                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"error\": \"Não autenticado\"}");
                        })
                )

                .authorizeHttpRequests(auth -> auth
                        // Fluxo OAuth2 — sempre público
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/login/**",
                                "/error",
                                "/favicon.ico",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Logout — qualquer autenticado
                        .requestMatchers(HttpMethod.POST, "/api/v1/command/auth/logout").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/v1/command/auth/logout-all").authenticated()

                        // Cancelamento — qualquer autenticado pode cancelar seus pedidos
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/command/orders/*"
                        ).authenticated()

                        // Query — qualquer autenticado (USER e ADMIN)
                        .requestMatchers("/api/v1/query/**").authenticated()

                        // Command — apenas ADMIN
                        .requestMatchers("/api/v1/command/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )

                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestRepository(cookieAuthorizationRequestRepository())
                        )
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2AuthSuccessHandler())
                )

                // JWT filter roda antes do filtro padrão de autenticação
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    @Bean
    public OAuth2AuthSucessHandler oAuth2AuthSuccessHandler() {
        return new OAuth2AuthSucessHandler(jwtTokenService, userCommandRepository, cookieTokenUtil, refreshTokenService);
    }

    // CORS CONFIG
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Apenas o frontend conhecido — nunca usar "*" com cookies
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        // Obrigatório para cookies cross-origin funcionarem
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}