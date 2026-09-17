package com.erpschool.calendar.repository;

import com.erpschool.calendar.entity.SchoolEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SchoolEventRepository extends JpaRepository<SchoolEvent, UUID> {
    List<SchoolEvent> findByTenantIdAndStartDateBetweenOrderByStartDateAsc(UUID tenantId, LocalDate from, LocalDate to);
    List<SchoolEvent> findByTenantIdOrderByStartDateAsc(UUID tenantId);
}
