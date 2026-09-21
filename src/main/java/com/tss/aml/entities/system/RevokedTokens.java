package com.tss.aml.entities.system;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "revoked_tokens", schema = "public")
@NoArgsConstructor
@AllArgsConstructor
public class RevokedTokens {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, name = "jti")
    private String jti;

    @Column(nullable = false, name = "expire_at")
    private LocalDateTime expireAt;

    @Column(nullable = false, name = "revoked-at")
    private LocalDateTime revokedAt = LocalDateTime.now();

}
