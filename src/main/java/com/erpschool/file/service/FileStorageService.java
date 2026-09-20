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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
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
        String original = file.getOriginalFilename() == null
                ? "file"
                : file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String contentType = normalizeContentType(file.getContentType(), original);
        if (!ALLOWED.contains(contentType)) {
            throw new BusinessException("FILE_TYPE", "File type is not allowed: " + contentType);
        }
        if ("image/jpg".equals(contentType)) {
            contentType = "image/jpeg";
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException("FILE_TOO_LARGE", "Maximum upload size is 10MB");
        }
        try {
            Path dir = root.resolve(tenantId.toString());
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + "-" + original;
            Path dest = dir.resolve(storedName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
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

    private static String normalizeContentType(String raw, String filename) {
        String contentType = raw == null ? "" : raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (contentType.isBlank() || "application/octet-stream".equals(contentType)) {
            contentType = inferFromName(filename);
        }
        return contentType;
    }

    private static String inferFromName(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".doc")) {
            return "application/msword";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return "application/octet-stream";
    }
}
