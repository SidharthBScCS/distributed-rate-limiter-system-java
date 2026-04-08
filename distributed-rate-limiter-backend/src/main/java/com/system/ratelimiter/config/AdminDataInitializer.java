package com.system.ratelimiter.config;

import com.system.ratelimiter.service.AdministratorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminDataInitializer {

    @Bean
    CommandLineRunner seedDefaultAdministrator(
            AdministratorService administratorService,
            @Value("${auth.admin.username:admin}") String username,
            @Value("${auth.admin.password:admin@2026}") String password,
            @Value("${auth.admin.full-name:API Administrator}") String fullName,
            @Value("${auth.admin.email:admin@ratelimiter.local}") String email
    ) {
        return args -> administratorService.ensureDefaultAdministrator(username, password, fullName, email);
    }
}
