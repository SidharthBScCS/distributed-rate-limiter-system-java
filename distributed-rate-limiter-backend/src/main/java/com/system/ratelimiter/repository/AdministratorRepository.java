package com.system.ratelimiter.repository;

import com.system.ratelimiter.entity.Administrator;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministratorRepository extends JpaRepository<Administrator, Long> {
    Optional<Administrator> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
}
