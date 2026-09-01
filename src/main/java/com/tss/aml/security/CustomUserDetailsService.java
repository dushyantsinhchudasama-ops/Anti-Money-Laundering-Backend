package com.tss.aml.security;

import com.tss.aml.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tss.aml.entities.system.Users;
// import your actual UsersRepository
// import your actual Tenant entity/repository

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository usersRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {

                String tenantCode = null;
                String lookupEmail = email;

                // support composite principal: "email||tenantCode"
                if (email != null && email.contains("||")) {
                        String[] parts = email.split("\\|\\|");
                        if (parts.length == 2) {
                                lookupEmail = parts[0];
                                tenantCode = parts[1];
                        }
                }

                Users user;

                if (tenantCode != null) {
                        user = usersRepository.findByEmailAndTenant_TenantCode(lookupEmail, tenantCode)
                                        .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
                } else {
                        user = usersRepository.findByEmail(lookupEmail)
                                        .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
                }

        return CustomUserDetails.builder()
                .userId(user.getUserId())
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" + user.getRole().name()
                        )
                ))
                .tenantId(user.getTenant().getTenantId())
                .tenantCode(user.getTenant().getTenantCode())
                .enabled(Boolean.TRUE.equals(user.getIsActive()))
                .accountNonLocked(
                        user.getLockedUntil() == null ||
                                user.getLockedUntil().isBefore(LocalDateTime.now())
                )
                .build();
    }
}