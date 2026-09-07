package com.tss.aml.repositories;

import com.tss.aml.entities.system.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    @Query("SELECT t FROM Tenant t WHERE t.tenantCode = LOWER(TRIM(:tenantCode))")
    Optional<Tenant> findByTenantCode(@Param("tenantCode") String tenantCode);

    Optional<Tenant> findBySchemaName(String schemaName);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Tenant t WHERE t.tenantCode = LOWER(TRIM(:tenantCode))")
    boolean existsByTenantCode(@Param("tenantCode") String tenantCode);
}