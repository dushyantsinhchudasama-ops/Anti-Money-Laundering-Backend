package com.tss.aml.services.interfaces;


import com.tss.aml.dtos.auth.LoginRequest;
import com.tss.aml.dtos.auth.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}