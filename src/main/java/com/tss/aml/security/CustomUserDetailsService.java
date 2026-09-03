package com.tss.aml.security;

import com.tss.aml.entities.system.SystemAdmin;
import com.tss.aml.entities.system.Users;
import com.tss.aml.repositories.SystemAdminRepository;
import com.tss.aml.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository usersRepository;
    private final SystemAdminRepository systemAdminRepository;

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

        if (tenantCode != null) {
            Users user = usersRepository.findByEmailAndTenant_TenantCode(lookupEmail, tenantCode)
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
            return buildCustomUserDetails(user);
        }

        // When tenantCode is null, first check if user is a SystemAdmin in system_admin table
        Optional<SystemAdmin> systemAdminOpt = systemAdminRepository.findByEmail(lookupEmail);
        if (systemAdminOpt.isPresent()) {
            return buildCustomUserDetails(systemAdminOpt.get());
        }

        // Otherwise check UserRepository for tenant users
        Users user = usersRepository.findByEmail(lookupEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
        return buildCustomUserDetails(user);
    }

    private CustomUserDetails buildCustomUserDetails(Users user) {
        return CustomUserDetails.builder()
                .userId(user.getUserId())
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" + user.getRole().name()
                        )
                ))
                .tenantId(user.getTenant() != null ? user.getTenant().getTenantId() : null)
                .tenantCode(user.getTenant() != null ? user.getTenant().getTenantCode() : null)
                .enabled(Boolean.TRUE.equals(user.getIsActive()))
                .accountNonLocked(
                        user.getLockedUntil() == null ||
                                user.getLockedUntil().isBefore(LocalDateTime.now())
                )
                .build();
    }

    private CustomUserDetails buildCustomUserDetails(SystemAdmin admin) {
        return CustomUserDetails.builder()
                .userId(admin.getSystemAdminId())
                .username(admin.getEmail())
                .password(admin.getPasswordHash())
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")
                ))
                .tenantId(null)
                .tenantCode(null)
                .enabled(Boolean.TRUE.equals(admin.getIsActive()))
                .accountNonLocked(
                        admin.getLockedUntil() == null ||
                                admin.getLockedUntil().isBefore(LocalDateTime.now())
                )
                .build();
    }
}