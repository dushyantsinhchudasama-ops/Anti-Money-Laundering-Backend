package com.tss.aml.controllers;

import com.tss.aml.dtos.auth.LoginRequest;
import com.tss.aml.dtos.auth.LoginResponse;
import com.tss.aml.services.interfaces.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(
                    authService.login(request)
            );
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).build();
        }
    }
}