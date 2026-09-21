package com.tss.aml.repositories;

import com.tss.aml.entities.system.RevokedTokens;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedTokens, UUID> {

    boolean existsByJti(String jti);
}
