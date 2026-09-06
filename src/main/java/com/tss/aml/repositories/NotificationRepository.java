package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipient_UserIdOrderByCreatedAtDesc(UUID recipientId);
    Page<Notification> findByRecipient_UserId(UUID recipientId, Pageable pageable);
    long countByRecipient_UserIdAndIsReadFalse(UUID recipientId);
}
