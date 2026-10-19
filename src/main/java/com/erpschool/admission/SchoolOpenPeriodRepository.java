package com.erpschool.admission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SchoolOpenPeriodRepository extends JpaRepository<SchoolOpenPeriod, UUID> {

    List<SchoolOpenPeriod> findByTenantIdOrderByDayOfWeekAscStartTimeAsc(UUID tenantId);

    List<SchoolOpenPeriod> findByTenantIdAndDayOfWeekAndEnabledTrue(UUID tenantId, int dayOfWeek);

    void deleteByTenantId(UUID tenantId);
}
