package com.erpschool.academic.repository;

import com.erpschool.academic.entity.TimetableSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TimetableSettingsRepository extends JpaRepository<TimetableSettings, UUID> {
}
