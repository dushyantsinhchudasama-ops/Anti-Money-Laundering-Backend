package com.tss.aml.repositories;

import com.tss.aml.entities.system.Users;
import com.tss.aml.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<Users, UUID> {
    @Query("SELECT u FROM Users u WHERE u.email = LOWER(TRIM(:email))")
    Optional<Users> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM Users u WHERE u.email = LOWER(TRIM(:email)) AND u.tenant.tenantCode = LOWER(TRIM(:tenantCode))")
    Optional<Users> findByEmailAndTenant_TenantCode(@Param("email") String email, @Param("tenantCode") String tenantCode);

    @Query("SELECT u FROM Users u WHERE u.userId = :id AND u.tenant.tenantCode = LOWER(TRIM(:tenantCode))")
    Optional<Users> findByUserIdAndTenant_TenantCode(@Param("id") UUID id, @Param("tenantCode") String tenantCode);

    Page<Users> findAllByTenant_TenantIdAndRole(UUID tenantId, UserRole userRole, Pageable pageable);
    java.util.List<Users> findByTenant_TenantIdAndRoleAndIsActiveTrue(UUID tenantId, UserRole role);
    Page<Users> findAllByTenant_TenantId(UUID tenantId, Pageable pageable);

    Optional<Users> findByUserCode(String userCode);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM Users u WHERE u.email = LOWER(TRIM(:email))")
    boolean existsByEmail(@Param("email") String email);

    boolean existsByUserCode(String userCode);
}

