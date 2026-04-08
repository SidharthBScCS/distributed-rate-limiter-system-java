package com.system.ratelimiter.service;

import com.system.ratelimiter.entity.Administrator;
import com.system.ratelimiter.repository.AdministratorRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdministratorPrincipalService implements UserDetailsService {

    private final AdministratorRepository administratorRepository;

    public AdministratorPrincipalService(AdministratorRepository administratorRepository) {
        this.administratorRepository = administratorRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Administrator administrator = administratorRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Administrator not found"));
        return toUserDetails(administrator);
    }

    public UserDetails toUserDetails(Administrator administrator) {
        return User.withUsername(administrator.getUsername())
                .password(administrator.getPassword())
                .disabled(!administrator.isEnabled())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + administrator.getRole())))
                .build();
    }
}
