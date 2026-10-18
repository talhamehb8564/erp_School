package com.erpschool.admission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionAppointmentRepository extends JpaRepository<AdmissionAppointment, UUID> {

    Optional<AdmissionAppointment> findByTenantIdAndApplicationIdAndStatus(
            UUID tenantId, UUID applicationId, AdmissionAppointmentStatus status);

    @Query("""
            SELECT a FROM AdmissionAppointment a
            WHERE a.tenantId = :tenantId
              AND a.status = :status
              AND a.slotStart >= :from
              AND a.slotStart < :to
              AND (:campusId IS NULL OR a.campusId = :campusId)
            """)
    List<AdmissionAppointment> findBookedInRange(@Param("tenantId") UUID tenantId,
                                                 @Param("campusId") UUID campusId,
                                                 @Param("from") Instant from,
                                                 @Param("to") Instant to,
                                                 @Param("status") AdmissionAppointmentStatus status);

    boolean existsByTenantIdAndCampusIdAndSlotStartAndStatus(
            UUID tenantId, UUID campusId, Instant slotStart, AdmissionAppointmentStatus status);

    boolean existsByTenantIdAndSlotStartAndStatusAndCampusIdIsNull(
            UUID tenantId, Instant slotStart, AdmissionAppointmentStatus status);
}
