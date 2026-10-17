package com.erpschool.audit.dto;

import com.erpschool.audit.entity.AuditLog;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class AuditLogResponse {

    private final UUID id;
    private final UUID tenantId;
    private final UUID userId;
    private final String username;
    private final String role;
    private final String action;
    private final String entityType;
    private final String entityId;
    private final Map<String, Object> details;
    private final String ipAddress;
    private final Instant createdAt;

    public static AuditLogResponse from(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .tenantId(log.getTenantId())
                .userId(log.getUserId())
                .username(log.getUsername())
                .role(log.getRole())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
