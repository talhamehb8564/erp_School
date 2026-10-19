package com.erpschool.academic.repository;

import com.erpschool.academic.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SectionRepository extends JpaRepository<Section, UUID> {
    List<Section> findByTenantIdAndClassIdOrderByNameAsc(UUID tenantId, UUID classId);
    List<Section> findByTenantId(UUID tenantId);
}
