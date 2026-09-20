package com.erpschool.fee.repository;

import com.erpschool.fee.entity.ChallanCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChallanChargeRepository extends JpaRepository<ChallanCharge, UUID> {
    List<ChallanCharge> findByChallanId(UUID challanId);
}
