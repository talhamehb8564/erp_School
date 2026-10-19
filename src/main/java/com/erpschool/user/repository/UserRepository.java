package com.erpschool.user.repository;

import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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
              AND (:campusPresent = false OR u.campusId = :campusId)
            """)
    Page<User> searchByTenant(@Param("tenantId") UUID tenantId,
                              @Param("rolePresent") boolean rolePresent,
                              @Param("role") UserRole role,
                              @Param("statusPresent") boolean statusPresent,
                              @Param("status") UserStatus status,
                              @Param("campusPresent") boolean campusPresent,
                              @Param("campusId") UUID campusId,
                              Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.tenantId = :tenantId
              AND (:rolePresent = false OR u.role = :role)
              AND (:statusPresent = false OR u.status = :status)
              AND (:campusPresent = false OR u.campusId = :campusId)
              AND (LOWER(u.username) LIKE :q
                   OR LOWER(u.firstName) LIKE :q
                   OR LOWER(u.lastName) LIKE :q
                   OR LOWER(COALESCE(u.email, '')) LIKE :q
                   OR LOWER(COALESCE(u.phone, '')) LIKE :q)
            """)
    Page<User> searchByTenantQuery(@Param("tenantId") UUID tenantId,
                                   @Param("rolePresent") boolean rolePresent,
                                   @Param("role") UserRole role,
                                   @Param("statusPresent") boolean statusPresent,
                                   @Param("status") UserStatus status,
                                   @Param("campusPresent") boolean campusPresent,
                                   @Param("campusId") UUID campusId,
                                   @Param("q") String q,
                                   Pageable pageable);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, UserStatus status);

    long countByTenantIdAndRole(UUID tenantId, UserRole role);

    long countByTenantIdAndCampusId(UUID tenantId, UUID campusId);

    long countByTenantIdAndCampusIdAndRole(UUID tenantId, UUID campusId, UserRole role);

    long countByTenantIdAndCampusIdAndStatus(UUID tenantId, UUID campusId, UserStatus status);

    java.util.List<User> findByTenantIdAndRole(UUID tenantId, UserRole role);

    java.util.List<User> findByTenantIdAndCampusIdAndRole(UUID tenantId, UUID campusId, UserRole role);

    long countByRole(UserRole role);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
            set u.status = :status, u.updatedAt = :now, u.updatedBy = :actorId
            where u.tenantId = :tenantId and u.id in :ids
            """)
    int updateStatusForIds(@Param("tenantId") UUID tenantId,
                           @Param("ids") Collection<UUID> ids,
                           @Param("status") UserStatus status,
                           @Param("now") Instant now,
                           @Param("actorId") UUID actorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
            set u.lastLoginAt = :at,
                u.failedLoginAttempts = 0,
                u.lockedUntil = null,
                u.status = :status
            where u.id = :id
            """)
    int markSuccessfulLogin(@Param("id") UUID id,
                            @Param("at") Instant at,
                            @Param("status") UserStatus status);
}
