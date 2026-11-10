package com.erpschool.admission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdmissionSettingsRepository extends JpaRepository<AdmissionSettings, UUID> {
}
