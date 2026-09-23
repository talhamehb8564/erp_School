package com.erpschool.user.repository;

import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<User> findByTenantId(UUID tenantId, Pageable pageable);

    java.util.List<User> findByTenantId(UUID tenantId);

    @Query("""
            SELECT u FROM User u
            WHERE u.tenantId = :tenantId
              AND (:rolePresent = false OR u.role = :role)
              AND (:statusPresent = false OR u.status = :status)
            """)
    Page<User> searchByTenant(@Param("tenantId") UUID tenantId,
                              @Param("rolePresent") boolean rolePresent,
                              @Param("role") UserRole role,
                              @Param("statusPresent") boolean statusPresent,
                              @Param("status") UserStatus status,
                              Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.tenantId = :tenantId
              AND (:rolePresent = false OR u.role = :role)
              AND (:statusPresent = false OR u.status = :status)
              AND (LOWER(u.username) LIKE :q
                   OR LOWER(u.firstName) LIKE :q
                   OR LOWER(u.lastName) LIKE :q
                   OR LOWER(COALESCE(u.email, '')) LIKE :q)
            """)
    Page<User> searchByTenantQuery(@Param("tenantId") UUID tenantId,
                                   @Param("rolePresent") boolean rolePresent,
                                   @Param("role") UserRole role,
                                   @Param("statusPresent") boolean statusPresent,
                                   @Param("status") UserStatus status,
                                   @Param("q") String q,
                                   Pageable pageable);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, UserStatus status);

    long countByTenantIdAndRole(UUID tenantId, UserRole role);

    java.util.List<User> findByTenantIdAndRole(UUID tenantId, UserRole role);

    long countByRole(UserRole role);
}
