package com.erpschool.feature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantFeatureRepository extends JpaRepository<TenantFeature, TenantFeature.Key> {

    List<TenantFeature> findByTenantId(UUID tenantId);
}
