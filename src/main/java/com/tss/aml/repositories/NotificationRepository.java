package com.tss.aml.repositories;

import com.tss.aml.entities.tenant.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @EntityGraph(attributePaths = {"recipient", "sarStr"})
    List<Notification> findByRecipient_UserIdOrderByCreatedAtDesc(UUID recipientId);

    @EntityGraph(attributePaths = {"recipient", "sarStr"})
    Page<Notification> findByRecipient_UserId(UUID recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"recipient", "sarStr"})
    Page<Notification> findByRecipient_UserIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"recipient", "sarStr"})
    Page<Notification> findByRecipient_UserIdAndIsReadFalseOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"recipient", "sarStr"})
    Optional<Notification> findByNotificationIdAndRecipient_UserId(UUID notificationId, UUID recipientId);

    boolean existsByRecipient_UserIdAndSarStr_SarStrId(UUID recipientId, UUID sarStrId);

    long countByRecipient_UserIdAndIsReadFalse(UUID recipientId);
}
