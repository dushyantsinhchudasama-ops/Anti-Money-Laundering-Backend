package com.tss.aml.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private Boolean mustResetPassword;
    private String tenantCode;
    private String userRole;
}
