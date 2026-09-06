package com.tss.aml.repositories;

import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<Users, UUID> {
    Optional<Users> findByEmail(String email);
    Optional<Users> findByEmailAndTenant_TenantCode(String email, String tenantCode);
    Optional<Users> findByUserIdAndTenant_TenantCode(UUID id, String tenantCode);
    Page<Users> findAllByTenant_TenantIdAndRole(UUID tenantId, UserRole userRole, Pageable pageable);
    Optional<Users> findByUserCode(String userCode);
    boolean existsByEmail(String email);
    boolean existsByUserCode(String userCode);
}

