package com.tss.aml.repositories;

import com.tss.aml.entities.system.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<Users, UUID> {
    Optional<Users> findByEmail(String email);
    Optional<Users> findByEmailAndTenant_TenantCode(String email, String tenantCode);
    Optional<Users> findByUserCode(String userCode);
    boolean existsByEmail(String email);
    boolean existsByUserCode(String userCode);
}

