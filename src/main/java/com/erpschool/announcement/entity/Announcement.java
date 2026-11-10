package com.erpschool.announcement.entity;

import com.erpschool.common.entity.TenantAwareEntity;
import com.erpschool.website.JsonLists;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "announcements")
public class Announcement extends TenantAwareEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 30)
    private String audience = "ALL";

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "class_id")
    private UUID classId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @JsonIgnore
    @Column(name = "attachments_json", columnDefinition = "TEXT")
    private String attachmentsJson;

    @Column(name = "publish_date", nullable = false)
    private LocalDate publishDate = LocalDate.now();

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    public String getAttachmentUrl() {
        String normalized = JsonLists.normalizeFileUrl(attachmentUrl);
        if (normalized != null) {
            return normalized;
        }
        List<Map<String, Object>> list = JsonLists.normalizeAttachments(attachmentsJson);
        if (!list.isEmpty() && list.get(0).get("fileUrl") != null) {
            return list.get(0).get("fileUrl").toString();
        }
        return null;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = JsonLists.normalizeFileUrl(attachmentUrl);
    }

    @JsonProperty("attachments")
    public List<Map<String, Object>> getAttachments() {
        return JsonLists.buildAttachments(this.attachmentsJson, this.attachmentUrl);
    }

    @JsonProperty("attachments")
    public void setAttachments(List<Map<String, Object>> items) {
        List<Map<String, Object>> normalized = JsonLists.normalizeAttachments(items);
        this.attachmentsJson = normalized.isEmpty() ? null : JsonLists.write(normalized);
        if ((this.attachmentUrl == null || this.attachmentUrl.isBlank()) && !normalized.isEmpty()) {
            Object firstUrl = normalized.get(0).get("fileUrl");
            if (firstUrl != null) {
                this.attachmentUrl = JsonLists.normalizeFileUrl(firstUrl.toString());
            }
        }
    }
}
