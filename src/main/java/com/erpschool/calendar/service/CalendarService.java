package com.erpschool.calendar.service;

import com.erpschool.calendar.entity.SchoolEvent;
import com.erpschool.calendar.repository.SchoolEventRepository;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.tenant.context.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CalendarService {

    private static final Set<String> TYPES = Set.of("HOLIDAY", "EVENT", "PTM", "EXAM", "ACTIVITY", "OTHER");

    private final SchoolEventRepository repository;

    public CalendarService(SchoolEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SchoolEvent create(SchoolEvent event) {
        if (!TYPES.contains(event.getEventType())) {
            event.setEventType("EVENT");
        }
        event.setTenantId(TenantGuard.requireTenantId(null));
        event.setCreatedBy(TenantContext.getUserId());
        return repository.save(event);
    }

    @Transactional(readOnly = true)
    public List<SchoolEvent> range(LocalDate from, LocalDate to) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        if (from == null || to == null) {
            return repository.findByTenantIdOrderByStartDateAsc(tenantId);
        }
        return repository.findByTenantIdAndStartDateBetweenOrderByStartDateAsc(tenantId, from, to);
    }

    @Transactional
    public SchoolEvent update(UUID id, SchoolEvent incoming) {
        SchoolEvent e = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event", id));
        TenantGuard.assertSameTenant(e.getTenantId());
        if (incoming.getTitle() != null) e.setTitle(incoming.getTitle());
        if (incoming.getDescription() != null) e.setDescription(incoming.getDescription());
        if (incoming.getEventType() != null) e.setEventType(incoming.getEventType());
        if (incoming.getStartDate() != null) e.setStartDate(incoming.getStartDate());
        if (incoming.getEndDate() != null) e.setEndDate(incoming.getEndDate());
        if (incoming.getAudience() != null) e.setAudience(incoming.getAudience());
        e.setUpdatedBy(TenantContext.getUserId());
        return repository.save(e);
    }
}
