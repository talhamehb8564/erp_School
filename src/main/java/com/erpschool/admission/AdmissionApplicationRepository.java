package com.erpschool.admission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionApplicationRepository extends JpaRepository<AdmissionApplication, UUID> {

    Page<AdmissionApplication> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<AdmissionApplication> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AdmissionApplication> findByTenantIdAndCnicAndStatusIn(
            UUID tenantId, String cnic, Collection<AdmissionApplicationStatus> statuses);
}
