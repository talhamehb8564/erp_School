package com.erpschool.settings.service;

import com.erpschool.campus.entity.Campus;
import com.erpschool.campus.repository.CampusRepository;
import com.erpschool.common.cache.CatalogCache;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.settings.entity.SchoolSettings;
import com.erpschool.settings.repository.SchoolSettingsRepository;
import com.erpschool.tenant.context.CampusScope;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class SchoolSettingsService {

    private final SchoolSettingsRepository repository;
    private final TenantRepository tenantRepository;
    private final CampusRepository campusRepository;
    private final CatalogCache catalogCache;

    public SchoolSettingsService(SchoolSettingsRepository repository,
                                 TenantRepository tenantRepository,
                                 CampusRepository campusRepository,
                                 CatalogCache catalogCache) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.campusRepository = campusRepository;
        this.catalogCache = catalogCache;
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
        s.setIban(incoming.getIban());
        s.setJazzcash(incoming.getJazzcash());
        s.setEasypaisa(incoming.getEasypaisa());
        s.setOtherPaymentMethods(incoming.getOtherPaymentMethods());
        s.setLogoUrl(incoming.getLogoUrl());
        s.setPrimaryColor(incoming.getPrimaryColor());
        s.setAccentColor(incoming.getAccentColor());
        s.setAbsentDeduction(incoming.getAbsentDeduction());
        s.setLateDeduction(incoming.getLateDeduction());
        s.setLeaveDeduction(incoming.getLeaveDeduction());
        s.setWaiveAttendanceDeduction(incoming.isWaiveAttendanceDeduction());
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(TenantContext.getUserId());
        SchoolSettings saved = repository.save(s);
        tenantRepository.findById(tenantId).ifPresent(tenant -> applyBranding(tenant, saved));
        if (CampusScope.restricts()) {
            campusRepository.findById(CampusScope.current()).ifPresent(campus -> applyCampus(campus, saved));
        }
        catalogCache.evictTenant(tenantId);
        return saved;
    }

    private void applyBranding(Tenant tenant, SchoolSettings saved) {
        if (saved.getLogoUrl() != null) {
            tenant.setLogoUrl(saved.getLogoUrl());
        }
        if (saved.getPrimaryColor() != null) {
            tenant.setPrimaryColor(saved.getPrimaryColor());
        }
        if (saved.getAccentColor() != null) {
            tenant.setAccentColor(saved.getAccentColor());
        }
        tenantRepository.save(tenant);
    }

    private void applyCampus(Campus campus, SchoolSettings saved) {
        if (saved.getLogoUrl() != null) {
            campus.setLogoUrl(saved.getLogoUrl());
        }
        if (saved.getPrimaryColor() != null) {
            campus.setPrimaryColor(saved.getPrimaryColor());
        }
        if (saved.getAccentColor() != null) {
            campus.setAccentColor(saved.getAccentColor());
        }
        campusRepository.save(campus);
    }
}
