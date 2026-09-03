package com.tss.aml.repositories;

import com.tss.aml.entities.system.SystemAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemAdminRepository extends JpaRepository<SystemAdmin, UUID> {

    Optional<SystemAdmin> findByEmail(String email);

    Optional<SystemAdmin> findBySystemAdminCode(String systemAdminCode);
}
