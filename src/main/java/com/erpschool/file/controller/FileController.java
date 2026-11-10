package com.erpschool.file.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.file.entity.StoredFile;
import com.erpschool.file.service.FileStorageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "Files")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("File uploaded", fileStorageService.store(file)));
    }

    @GetMapping("/{tenantId}/{name:.+}")
    public ResponseEntity<Resource> download(@PathVariable UUID tenantId, @PathVariable String name) throws Exception {
        if (name == null || name.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        final Path namePath;
        try {
            namePath = Path.of(name);
        } catch (InvalidPathException ex) {
            return ResponseEntity.notFound().build();
        }
        if (namePath.getNameCount() != 1 || namePath.isAbsolute() || name.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        Path path = fileStorageService.resolvePublic(tenantId, name);
        Optional<StoredFile> storedRecord = fileStorageService.findStoredFile(tenantId, name);
        Resource resource = null;
        if (Files.exists(path) && Files.isRegularFile(path)) {
            resource = new FileSystemResource(path);
        } else if (storedRecord.isPresent()
                && storedRecord.get().getFileData() != null
                && storedRecord.get().getFileData().length > 0) {
            byte[] bytes = storedRecord.get().getFileData();
            try {
                Files.createDirectories(path.getParent());
                Files.write(path, bytes);
                resource = new FileSystemResource(path);
            } catch (Exception ignored) {
                resource = new ByteArrayResource(bytes);
            }
        }
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        String contentType = storedRecord
                .map(StoredFile::getContentType)
                .filter(ct -> !ct.isBlank() && !"application/octet-stream".equalsIgnoreCase(ct))
                .orElse(null);
        if (contentType == null) {
            String inferred = FileStorageService.inferFromName(name);
            if (!"application/octet-stream".equals(inferred)) {
                contentType = inferred;
            }
        }
        if (contentType == null && Files.exists(path)) {
            contentType = Files.probeContentType(path);
        }
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (contentType != null && !contentType.isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (InvalidMediaTypeException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        /* X-Content-Type-Options: nosniff is set globally in SecurityConfig.
           Repeat it here with the literal header name so uploaded files stay
           protected even on Spring versions that lack HttpHeaders.X_CONTENT_TYPE_OPTIONS. */
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safe + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(mediaType)
                .body(resource);
    }
}
