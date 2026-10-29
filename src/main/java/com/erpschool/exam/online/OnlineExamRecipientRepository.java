package com.erpschool.exam.online;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlineExamRecipientRepository extends JpaRepository<OnlineExamRecipient, UUID> {

    List<OnlineExamRecipient> findByTenantIdAndExamId(UUID tenantId, UUID examId);

    List<OnlineExamRecipient> findByTenantIdAndExamIdIn(UUID tenantId, Collection<UUID> examIds);

    Optional<OnlineExamRecipient> findByTenantIdAndExamIdAndStudentId(UUID tenantId, UUID examId, UUID studentId);

    boolean existsByTenantIdAndExamId(UUID tenantId, UUID examId);

    boolean existsByTenantIdAndExamIdAndExcludedFalse(UUID tenantId, UUID examId);

    void deleteByTenantIdAndExamId(UUID tenantId, UUID examId);
}
