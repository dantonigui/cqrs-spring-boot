package com.project.cqrs.command.auth.service;

import com.project.cqrs.command.auth.infra.kafka.UserEventProducer;
import com.project.cqrs.command.auth.model.UserCommandEntity;
import com.project.cqrs.command.auth.model.UserRole;
import com.project.cqrs.command.auth.repository.UserCommandRepository;
import com.project.cqrs.shared.event.user.UserCreatedEvent;
import com.project.cqrs.shared.event.user.UserUpdatedEvent;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserCommandRepository userCommandRepository;
    private final UserEventProducer userEventProducer;

    public CustomOAuth2UserService(UserCommandRepository userCommandRepository, UserEventProducer userEventProducer) {
        this.userCommandRepository = userCommandRepository;
        this.userEventProducer = userEventProducer;
    }

    @Override
    public OAuth2User loadUser (OAuth2UserRequest request) {
        OAuth2User oAuth2User = super.loadUser(request);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        String googleId = (String) attributes.get("googleId");
        String name = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");

        UserCommandEntity userCommandEntity = userCommandRepository.findByGoogleId(googleId)
                .map(existing -> {
                    existing.syncFromGoogle(email);
                    UserCommandEntity saved =  userCommandRepository.save(existing);

                    userEventProducer.publishUserUpdatedEvent(UserUpdatedEvent.userUpdatedEvent(existing));

                    return  saved;
                }).orElseGet(() -> {
                    UserCommandEntity newUser = UserCommandEntity.createUser(name, email, picture, googleId, UserRole.USER);

                    UserCommandEntity saved = userCommandRepository.save(newUser);

                    userEventProducer.publishUserCreatedEvent(UserCreatedEvent.createdEvent(saved));

                    return saved;
                });

        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_" + userCommandEntity.getUserRole().name())), attributes, "sub");
    }
}
