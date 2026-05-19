package com.project.cqrs.query.auth.repository;


import com.project.cqrs.query.auth.model.UserQueryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserQueryRepository extends JpaRepository<UserQueryEntity,Long> {
}
