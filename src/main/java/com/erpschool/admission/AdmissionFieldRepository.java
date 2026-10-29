package com.erpschool.admission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdmissionFieldRepository extends JpaRepository<AdmissionField, UUID> {

    List<AdmissionField> findByTenantIdOrderBySortOrderAsc(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);

    boolean existsByTenantIdAndFieldKey(UUID tenantId, String fieldKey);
}
