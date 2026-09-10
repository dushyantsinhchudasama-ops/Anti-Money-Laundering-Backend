package com.tss.aml.dtos.tenant;

import com.tss.aml.enums.NotificationChannel;
import com.tss.aml.enums.NotificationEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID notificationId;
    private UUID recipientId;
    private String recipientName;
    private UUID sarStrId;
    private NotificationEventType eventType;
    private NotificationChannel channel;
    private String message;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
