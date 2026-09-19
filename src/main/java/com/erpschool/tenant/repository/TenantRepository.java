package com.erpschool.tenant.repository;

import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.entity.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    Optional<Tenant> findByCodeIgnoreCase(String code);

    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);

    @Query("""
            SELECT t FROM Tenant t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:q IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(t.code) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Tenant> search(@Param("status") TenantStatus status,
                        @Param("q") String q,
                        Pageable pageable);
}
