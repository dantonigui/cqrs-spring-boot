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
    private final HttpCookieOAuth2AuthorizationRequestRepository oauthRepository;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          JwtAuthFilter jwtAuthFilter,
                          JwtTokenService jwtTokenService,
                          UserCommandRepository userCommandRepository,
                          CookieTokenUtil cookieTokenUtil,
                          RefreshTokenService refreshTokenService, HttpCookieOAuth2AuthorizationRequestRepository oauthRepository) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.jwtAuthFilter = jwtAuthFilter;
        this.jwtTokenService = jwtTokenService;
        this.userCommandRepository = userCommandRepository;
        this.cookieTokenUtil = cookieTokenUtil;
        this.refreshTokenService = refreshTokenService;
        this.oauthRepository = oauthRepository;
    }



    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/v1/webhooks/mercadopago"))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // JWT via cookie, sem sessão HTTP; sessões revogáveis mantidas no Redis
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
                                "/favicon.ico", "/api/v1/csrf", "/actuator/health",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**", "/swagger", "/api-docs/**"
                        ).permitAll()

                        // Logout — qualquer autenticado
                        .requestMatchers(HttpMethod.POST, "/api/v1/command/auth/logout").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/v1/command/auth/logout-all").authenticated()

                        // Cancelamento — qualquer autenticado pode cancelar seus pedidos
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/command/orders/*"
                        ).authenticated()

                        // Query — qualquer autenticado (USER e ADMIN)
//                        .requestMatchers("/api/v1/query/**").authenticated()
                                .requestMatchers("/admin/**", "/actuator/**").hasRole("ADMIN")
                                .requestMatchers("/api/v1/query/**").authenticated()
                        // Command — apenas ADMIN
//                        .requestMatchers("/api/v1/command/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/mercadopago").permitAll()
                                 .requestMatchers(HttpMethod.POST, "/api/v1/command/orders/*/checkout/in-person").hasRole("ADMIN")
                                 .requestMatchers("/api/v1/command/orders/**").authenticated()
                                 .requestMatchers("/api/v1/command/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )

                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestRepository(oauthRepository)
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
        OAuth2AuthSucessHandler handler = new OAuth2AuthSucessHandler(jwtTokenService, userCommandRepository, cookieTokenUtil, refreshTokenService);
        handler.setFrontendUrl(frontendUrl);
        return handler;
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