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

    @Query("""
            SELECT u FROM User u
            WHERE u.tenantId = :tenantId
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:q IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<User> searchByTenant(@Param("tenantId") UUID tenantId,
                              @Param("role") UserRole role,
                              @Param("status") UserStatus status,
                              @Param("q") String q,
                              Pageable pageable);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, UserStatus status);

    long countByTenantIdAndRole(UUID tenantId, UserRole role);

    java.util.List<User> findByTenantIdAndRole(UUID tenantId, UserRole role);

    long countByRole(UserRole role);
}
