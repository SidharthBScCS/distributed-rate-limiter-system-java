package com.system.ratelimiter.service;

import com.system.ratelimiter.entity.Administrator;
import com.system.ratelimiter.repository.AdministratorRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministratorService {

    private final AdministratorRepository administratorRepository;
    private final PasswordEncoder passwordEncoder;

    public AdministratorService(AdministratorRepository administratorRepository, PasswordEncoder passwordEncoder) {
        this.administratorRepository = administratorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Administrator getByUsername(String username) {
        return administratorRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
    }

    public List<Administrator> findAll() {
        return administratorRepository.findAll();
    }

    @Transactional
    public Administrator updateProfile(String username, String fullName, String email) {
        Administrator administrator = getByUsername(username);
        administrator.setFullName(fullName.trim());
        administrator.setEmail(email.trim());
        return administratorRepository.save(administrator);
    }

    @Transactional
    public void ensureDefaultAdministrator(String username, String password, String fullName, String email) {
        if (administratorRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        Administrator administrator = new Administrator();
        administrator.setUsername(username.trim());
        administrator.setPassword(passwordEncoder.encode(password));
        administrator.setFullName(fullName.trim());
        administrator.setEmail(email.trim());
        administrator.setRole("ADMIN");
        administrator.setEnabled(true);
        administratorRepository.save(administrator);
    }
}
