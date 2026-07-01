package com.project.cqrs.command.auth.infra.security;

import com.project.cqrs.command.auth.dto.UserRequestDTO;
import com.project.cqrs.command.auth.infra.cookie.CookieTokenUtil;
import com.project.cqrs.command.auth.model.UserCommandEntity;
import com.project.cqrs.command.auth.repository.UserCommandRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;

public class OAuth2AuthSucessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenService jwtTokenService;
    private final UserCommandRepository userCommandRepository;
    private final CookieTokenUtil cookieTokenUtil;
    private final RefreshTokenService refreshTokenService;

    public OAuth2AuthSucessHandler(JwtTokenService jwtTokenService, UserCommandRepository userCommandRepository, CookieTokenUtil cookieTokenUtil,  RefreshTokenService refreshTokenService) {
        this.jwtTokenService = jwtTokenService;
        this.userCommandRepository = userCommandRepository;
        this.cookieTokenUtil = cookieTokenUtil;
        this.refreshTokenService = refreshTokenService;
    }

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        var oAuth2User = (DefaultOAuth2User) authentication.getPrincipal();
        String googleId = oAuth2User.getAttribute("sub");

        UserCommandEntity  userCommandEntity = userCommandRepository.findByUserGoogleId(googleId)
                .orElseThrow(() -> new IllegalStateException("User not found with googleId: " + googleId));

        UserRequestDTO userDto = UserRequestDTO.from(userCommandEntity);

        String accessToken = jwtTokenService.generateToken(userDto);
        cookieTokenUtil.writeToken(response,accessToken);

        String refreshToken = refreshTokenService.generate(userCommandEntity.getUserId().toString());
        cookieTokenUtil.writeRefreshToken(response, refreshToken);

        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/");
    }
}
