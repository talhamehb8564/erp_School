package com.erpschool.audit.repository;

import com.erpschool.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * Avoid {@code :tenantId IS NULL OR a.tenantId = :tenantId}. A null UUID bind
     * becomes bytea on PostgreSQL ({@code uuid = bytea} / {@code lower(bytea)}).
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:tenantPresent = false OR a.tenantId = :tenantId)
              AND (:actionPresent = false OR a.action = :action)
              AND (:entityPresent = false OR a.entityType = :entityType)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(@Param("tenantPresent") boolean tenantPresent,
                          @Param("tenantId") UUID tenantId,
                          @Param("actionPresent") boolean actionPresent,
                          @Param("action") String action,
                          @Param("entityPresent") boolean entityPresent,
                          @Param("entityType") String entityType,
                          Pageable pageable);
}
