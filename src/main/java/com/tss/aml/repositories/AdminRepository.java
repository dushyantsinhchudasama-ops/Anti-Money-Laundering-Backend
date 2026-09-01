package com.tss.aml.repositories;

import com.tss.aml.entities.system.SystemAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminRepository extends JpaRepository<SystemAdmin, UUID> {
	Optional<SystemAdmin> findByEmail(String email);
}
