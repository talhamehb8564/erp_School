package com.erpschool.file.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.file.service.FileStorageService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @GetMapping("/{tenantId}/{name}")
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
        if (namePath.getNameCount() != 1 || namePath.isAbsolute()) {
            return ResponseEntity.notFound().build();
        }
        Path path = fileStorageService.resolvePublic(tenantId, name);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(path);
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
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }
}
