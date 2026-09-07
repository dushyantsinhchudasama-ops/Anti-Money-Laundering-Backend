package com.tss.aml.repositories;

import com.tss.aml.entities.system.SystemAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemAdminRepository extends JpaRepository<SystemAdmin, UUID> {

    @Query("SELECT sa FROM SystemAdmin sa WHERE sa.email = LOWER(TRIM(:email))")
    Optional<SystemAdmin> findByEmail(@Param("email") String email);

    Optional<SystemAdmin> findBySystemAdminCode(String systemAdminCode);
}
