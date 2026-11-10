package com.erpschool.exam.online;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlineExamRepository extends JpaRepository<OnlineExam, UUID> {
    List<OnlineExam> findByTenantIdOrderByStartAtDesc(UUID tenantId);
    List<OnlineExam> findByTenantIdAndCampusIdOrderByStartAtDesc(UUID tenantId, UUID campusId);
    List<OnlineExam> findByTenantIdAndCreatedByOrderByStartAtDesc(UUID tenantId, UUID createdBy);
    List<OnlineExam> findByTenantIdAndClassIdAndSectionIdOrderByStartAtDesc(UUID tenantId, UUID classId, UUID sectionId);
    Optional<OnlineExam> findByIdAndTenantId(UUID id, UUID tenantId);
}
