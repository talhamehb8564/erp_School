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
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final long IMAGE_MAX = 10L * 1024 * 1024;
    private static final long VIDEO_MAX = 25L * 1024 * 1024;

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
        boolean video = contentType.startsWith("video/");
        long max = video ? VIDEO_MAX : IMAGE_MAX;
        if (file.getSize() > max) {
            throw new BusinessException("FILE_TOO_LARGE",
                    video ? "Maximum video size is 25MB" : "Maximum upload size is 10MB");
        }
        try {
            assertMagic(contentType, file.getInputStream().readNBytes(16));
        } catch (IOException e) {
            throw new BusinessException("FILE_TYPE", "Could not read the uploaded file");
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
            throw new BusinessException("FILE_STORE_FAILED", "Could not store the file. Check that uploads are writable.");
        }
    }

    public Path resolvePublic(UUID tenantId, String name) {
        Path tenantDir = root.resolve(tenantId.toString()).normalize();
        Path dest = tenantDir.resolve(name).normalize();
        if (!dest.startsWith(tenantDir)) {
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
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".webm")) {
            return "video/webm";
        }
        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (lower.endsWith(".doc")) {
            return "application/msword";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return "application/octet-stream";
    }

    private static void assertMagic(String contentType, byte[] head) {
        if (head == null || head.length < 4) {
            throw new BusinessException("FILE_TYPE", "File content could not be verified");
        }
        if (contentType.startsWith("image/jpeg") && unsigned(head[0]) == 0xFF && unsigned(head[1]) == 0xD8) {
            return;
        }
        if ("image/png".equals(contentType) && unsigned(head[0]) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return;
        }
        if ("image/gif".equals(contentType) && head[0] == 'G' && head[1] == 'I' && head[2] == 'F') {
            return;
        }
        if ("image/webp".equals(contentType) && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F') {
            return;
        }
        if ("application/pdf".equals(contentType) && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
            return;
        }
        if (contentType.startsWith("video/") && head.length >= 8 && (
                (head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p')
                        || (unsigned(head[0]) == 0x1A && unsigned(head[1]) == 0x45)
                        || (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'))) {
            return;
        }
        if ((contentType.contains("wordprocessingml") || "application/msword".equals(contentType))
                && ((head[0] == 'P' && head[1] == 'K') || unsigned(head[0]) == 0xD0)) {
            return;
        }
        throw new BusinessException("FILE_TYPE", "File content does not match the declared type");
    }

    private static int unsigned(byte b) {
        return b & 0xFF;
    }
}
