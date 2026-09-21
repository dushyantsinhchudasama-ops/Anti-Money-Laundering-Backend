package com.tss.aml.services.implementation;

import com.tss.aml.entities.system.RevokedTokens;
import com.tss.aml.repositories.RevokedTokenRepository;
import com.tss.aml.security.JwtTokenProvider;
import com.tss.aml.services.interfaces.LogoutService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LogoutServiceImpl implements LogoutService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;


    @Override
    @Transactional
    public void logout(String token) {
        System.out.println("00000000000000000000000000000000000000");
        String jti = jwtTokenProvider.getJei(token);

        if(revokedTokenRepository.existsByJti(jti))
            return;

        LocalDateTime expireAt = jwtTokenProvider.getExpiration(token);

        //checking for already expired token
        if(expireAt.isBefore(LocalDateTime.now()))
            return;

        RevokedTokens revokedToken = new RevokedTokens();

        revokedToken.setJti(jti);
        revokedToken.setExpireAt(expireAt);

        revokedTokenRepository.save(revokedToken);
    }

    @Override
    @Transactional()
    public boolean isRevoked(String jti) {
        return revokedTokenRepository.existsByJti(jti);
    }
}
