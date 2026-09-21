package com.tss.aml.services.interfaces;

public interface LogoutService {

    void logout(String token);

    boolean isRevoked(String token);
}
