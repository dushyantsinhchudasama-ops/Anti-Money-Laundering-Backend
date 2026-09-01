package com.tss.aml.entities.tenant;

import com.tss.aml.entities.common.BaseEntity;
import com.tss.aml.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Bank Admin or Compliance Officer within this tenant's schema (SRS 3.2 / 3.3).
 */
@Data
@Entity
@Table(name = "tenant_user")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TenantUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_code", nullable = false, length = 20)
    private String userCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "user_role_enum")
    private UserRole role;

    @Column(name = "employee_id", length = 50)
    private String employeeId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 512)
    private String passwordHash;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Forced first-login reset (SRS 3.2.1 — "cannot be bypassed")
    @Builder.Default
    @Column(name = "must_reset_password", nullable = false)
    private Boolean mustResetPassword = true;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    // Account lockout (SRS 3.1.1)
    @Builder.Default
    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
}
