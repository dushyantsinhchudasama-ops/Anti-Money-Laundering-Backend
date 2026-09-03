package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
}
