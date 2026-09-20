package com.erpschool.settings.repository;

import com.erpschool.settings.entity.SchoolSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SchoolSettingsRepository extends JpaRepository<SchoolSettings, UUID> {
}
