package com.tss.aml.services.implementation;

import com.tss.aml.dtos.auth.LoginRequest;
import com.tss.aml.dtos.auth.LoginResponse;
import com.tss.aml.dtos.auth.ResetPasswordRequest;
import com.tss.aml.entities.system.Users;
import com.tss.aml.repositories.UserRepository;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.security.JwtTokenProvider;
import com.tss.aml.security.PasswordPolicyValidator;
import com.tss.aml.services.interfaces.AuthService;
import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;

    @Override
    public LoginResponse login(LoginRequest request) {

        String normalizedEmail = com.tss.aml.util.NormalizationUtils.normalizeEmail(request.getEmail());
        String normalizedTenantCode = com.tss.aml.util.NormalizationUtils.normalizeTenantCode(request.getTenantCode());

        // include tenantCode in the principal so CustomUserDetailsService can scope
        // lookup
        String principal = normalizedEmail;
        if (normalizedTenantCode != null && !normalizedTenantCode.isBlank()) {
            principal = normalizedEmail + "||" + normalizedTenantCode;
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        request.getPassword()
                )
        );

        String token = jwtTokenProvider.generateToken(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String roleStr = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse(null);

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .mustResetPassword(userDetails.isMustResetPassword())
                .tenantCode(userDetails.getTenantCode())
                .userRole(roleStr)
                .build();
    }

    @Override
    @Transactional
    public LoginResponse resetPassword(ResetPasswordRequest request) {
        if (request.getNewPassword() == null || !request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        String normalizedEmail = com.tss.aml.util.NormalizationUtils.normalizeEmail(request.getEmail());
        String normalizedTenantCode = com.tss.aml.util.NormalizationUtils.normalizeTenantCode(request.getTenantCode());

        Users user;
        if (normalizedTenantCode != null && !normalizedTenantCode.isBlank()) {
            user = userRepository.findByEmailAndTenant_TenantCode(normalizedEmail, normalizedTenantCode)
                    .orElseThrow(() -> new BadCredentialsException("User not found for provided email and tenant"));
        } else {
            user = userRepository.findByEmail(normalizedEmail)
                    .orElseThrow(() -> new BadCredentialsException("User not found for provided email"));
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        passwordPolicyValidator.validate(request.getNewPassword(), request.getCurrentPassword());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustResetPassword(false);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(user.getEmail());
        loginRequest.setPassword(request.getNewPassword());
        if (user.getTenant() != null) {
            loginRequest.setTenantCode(user.getTenant().getTenantCode());
        }

        return login(loginRequest);
    }
}