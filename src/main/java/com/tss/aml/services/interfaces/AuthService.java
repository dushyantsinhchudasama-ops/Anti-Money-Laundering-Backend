package com.tss.aml.services.interfaces;

import com.tss.aml.dtos.auth.LoginRequest;
import com.tss.aml.dtos.auth.LoginResponse;
import com.tss.aml.dtos.auth.ResetPasswordRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse resetPassword(ResetPasswordRequest request);
}