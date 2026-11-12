package com.project.cqrs.command.auth.infra.cookie;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;

@Component
public class HttpCookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private static final String COOKIE = "oauth2_auth_request";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean secure;
    public HttpCookieOAuth2AuthorizationRequestRepository(StringRedisTemplate redis, ObjectMapper mapper, @Value("${app.cookie.secure:true}") boolean secure) {
        this.redis = redis; this.mapper = mapper; this.secure = secure;
    }
    public record StoredRequest(String authorizationUri, String clientId, String redirectUri, Set<String> scopes, String state, Map<String, Object> additionalParameters, Map<String, Object> attributes) {}
    @Override public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) { return load(request, false); }
    @Override public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        var result = load(request, true); cookie(response, "", 0); return result;
    }
    @Override public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorization, HttpServletRequest request, HttpServletResponse response) {
        if (authorization == null) { removeAuthorizationRequest(request, response); return; }
        try {
            String id = UUID.randomUUID().toString();
            var stored = new StoredRequest(authorization.getAuthorizationUri(), authorization.getClientId(), authorization.getRedirectUri(), authorization.getScopes(), authorization.getState(), authorization.getAdditionalParameters(), authorization.getAttributes());
            redis.opsForValue().set("oauth2_request:" + id, mapper.writeValueAsString(stored), Duration.ofMinutes(3));
            cookie(response, id, 180);
        } catch (Exception e) { throw new IllegalStateException("Não foi possível iniciar OAuth2", e); }
    }
    private OAuth2AuthorizationRequest load(HttpServletRequest request, boolean remove) {
        if (request.getCookies() == null) return null;
        var id = Arrays.stream(request.getCookies()).filter(c -> COOKIE.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
        if (id == null || !id.matches("[0-9a-f-]{36}")) return null;
        String value = remove ? redis.opsForValue().getAndDelete("oauth2_request:" + id) : redis.opsForValue().get("oauth2_request:" + id);
        if (value == null) return null;
        try {
            var stored = mapper.readValue(value, StoredRequest.class);
            return OAuth2AuthorizationRequest.authorizationCode().authorizationUri(stored.authorizationUri()).clientId(stored.clientId()).redirectUri(stored.redirectUri()).scopes(stored.scopes()).state(stored.state()).additionalParameters(stored.additionalParameters()).attributes(stored.attributes()).build();
        } catch (Exception e) { return null; }
    }
    private void cookie(HttpServletResponse response, String value, int age) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(age).build().toString());
    }
}