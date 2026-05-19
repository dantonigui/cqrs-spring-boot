package com.project.cqrs.command.auth.repository;

import com.project.cqrs.command.auth.model.UserCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCommandRepository extends JpaRepository<UserCommandEntity, Long> {

    Optional<UserCommandEntity> findByGoogleId(String googleId);

}
