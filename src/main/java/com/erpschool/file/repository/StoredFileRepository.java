package com.erpschool.file.repository;

import com.erpschool.file.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findFirstByTenantIdAndStoredPathOrderByCreatedAtDesc(UUID tenantId, String storedPath);
}
