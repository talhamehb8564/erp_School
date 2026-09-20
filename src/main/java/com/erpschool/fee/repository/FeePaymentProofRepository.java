package com.erpschool.fee.repository;

import com.erpschool.fee.entity.ChallanStatus;
import com.erpschool.fee.entity.FeePaymentProof;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeePaymentProofRepository extends JpaRepository<FeePaymentProof, UUID> {
    List<FeePaymentProof> findByChallanIdOrderByCreatedAtDesc(UUID challanId);
    List<FeePaymentProof> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, ChallanStatus status);
}
