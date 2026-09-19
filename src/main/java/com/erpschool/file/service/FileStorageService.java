package com.erpschool.file.service;

import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.file.entity.StoredFile;
import com.erpschool.file.repository.StoredFileRepository;
import com.erpschool.tenant.context.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final StoredFileRepository repository;
    private final Path root;

    public FileStorageService(StoredFileRepository repository,
                              @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.repository = repository;
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional
    public Map<String, Object> store(MultipartFile file) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "File is required");
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!ALLOWED.contains(contentType)) {
            throw new BusinessException("FILE_TYPE", "File type is not allowed: " + contentType);
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException("FILE_TOO_LARGE", "Maximum upload size is 10MB");
        }
        try {
            Path dir = root.resolve(tenantId.toString());
            Files.createDirectories(dir);
            String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
            String storedName = UUID.randomUUID() + "-" + original;
            Path dest = dir.resolve(storedName);
            file.transferTo(dest);
            StoredFile entity = new StoredFile();
            entity.setTenantId(tenantId);
            entity.setOriginalName(original);
            entity.setStoredPath("/api/v1/files/" + tenantId + "/" + storedName);
            entity.setContentType(contentType);
            entity.setSizeBytes(file.getSize());
            entity.setCreatedBy(TenantContext.getUserId());
            entity = repository.save(entity);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("id", entity.getId());
            result.put("url", entity.getStoredPath());
            result.put("fileName", entity.getOriginalName());
            result.put("contentType", entity.getContentType());
            result.put("sizeBytes", entity.getSizeBytes());
            return result;
        } catch (IOException e) {
            throw new BusinessException("FILE_STORE_FAILED", "Could not store file");
        }
    }

    public Path resolvePublic(UUID tenantId, String name) {
        TenantGuard.assertSameTenant(tenantId);
        Path dest = root.resolve(tenantId.toString()).resolve(name).normalize();
        if (!dest.startsWith(root)) {
            throw new BusinessException("FILE_PATH", "Invalid file path");
        }
        return dest;
    }
}
