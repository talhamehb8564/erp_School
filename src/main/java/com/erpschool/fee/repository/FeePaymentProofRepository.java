package com.erpschool.fee.repository;

import com.erpschool.fee.entity.ChallanStatus;
import com.erpschool.fee.entity.FeePaymentProof;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeePaymentProofRepository extends JpaRepository<FeePaymentProof, UUID> {
    List<FeePaymentProof> findByChallanIdOrderByCreatedAtDesc(UUID challanId);

    List<FeePaymentProof> findByChallanIdInOrderByCreatedAtDesc(Collection<UUID> challanIds);
    List<FeePaymentProof> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, ChallanStatus status);

    @Query("""
            select p from FeePaymentProof p
            where p.tenantId = :tenantId and p.status = :status
              and (:scoped = false or exists (
                select 1 from FeeChallan f, com.erpschool.student.entity.Student s
                where f.id = p.challanId and s.id = f.studentId and s.campusId = :campusId
              ))
            order by p.createdAt desc
            """)
    List<FeePaymentProof> findPendingScoped(
            @Param("tenantId") UUID tenantId,
            @Param("status") ChallanStatus status,
            @Param("scoped") boolean scoped,
            @Param("campusId") UUID campusId,
            Pageable pageable);
}
