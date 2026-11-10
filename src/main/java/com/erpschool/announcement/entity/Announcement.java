package com.erpschool.announcement.entity;

import com.erpschool.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
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

    @Column(name = "publish_date", nullable = false)
    private LocalDate publishDate = LocalDate.now();

    @Column(name = "expiry_date")
    private LocalDate expiryDate;
}
