package com.dronzer.aisearch.repository;

import com.dronzer.aisearch.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByEmail(
            String email);
}