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

    /**
     * No LOWER()/CONCAT() on bind parameters. Hibernate 6 + PostgreSQL infers
     * a null {@code :q} as bytea, which produces {@code function lower(bytea) does not exist}.
     * Search text is applied only to varchar columns; the like-pattern is built in Java.
     */
    @Query("""
            SELECT t FROM Tenant t
            WHERE (:statusPresent = false OR t.status = :status)
            """)
    Page<Tenant> listFiltered(@Param("statusPresent") boolean statusPresent,
                              @Param("status") TenantStatus status,
                              Pageable pageable);

    @Query("""
            SELECT t FROM Tenant t
            WHERE (:statusPresent = false OR t.status = :status)
              AND (LOWER(t.name) LIKE :q OR LOWER(t.code) LIKE :q)
            """)
    Page<Tenant> search(@Param("statusPresent") boolean statusPresent,
                        @Param("status") TenantStatus status,
                        @Param("q") String q,
                        Pageable pageable);
}
