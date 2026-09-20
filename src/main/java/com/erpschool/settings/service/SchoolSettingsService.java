package com.erpschool.settings.service;

import com.erpschool.common.util.TenantGuard;
import com.erpschool.settings.entity.SchoolSettings;
import com.erpschool.settings.repository.SchoolSettingsRepository;
import com.erpschool.tenant.context.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class SchoolSettingsService {

    private final SchoolSettingsRepository repository;

    public SchoolSettingsService(SchoolSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SchoolSettings get() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        return repository.findById(tenantId).orElseGet(() -> {
            SchoolSettings s = new SchoolSettings();
            s.setTenantId(tenantId);
            return s;
        });
    }

    @Transactional
    public SchoolSettings save(SchoolSettings incoming) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        SchoolSettings s = repository.findById(tenantId).orElseGet(SchoolSettings::new);
        if (s.getTenantId() == null) {
            s.setTenantId(tenantId);
            s.setCreatedAt(Instant.now());
            s.setCreatedBy(TenantContext.getUserId());
        }
        s.setPaymentInstructions(incoming.getPaymentInstructions());
        s.setBankName(incoming.getBankName());
        s.setAccountTitle(incoming.getAccountTitle());
        s.setAccountNumber(incoming.getAccountNumber());
        s.setJazzcash(incoming.getJazzcash());
        s.setEasypaisa(incoming.getEasypaisa());
        s.setOtherPaymentMethods(incoming.getOtherPaymentMethods());
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(TenantContext.getUserId());
        return repository.save(s);
    }
}
