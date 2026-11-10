package com.erpschool.admission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "admission_settings")
public class AdmissionSettings {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "appointment_enabled", nullable = false)
    private boolean appointmentEnabled = false;

    @Column(name = "appointment_required", nullable = false)
    private boolean appointmentRequired = false;

    @Column(name = "cta_label", nullable = false, length = 80)
    private String ctaLabel = "Apply for Admission";

    @Column(name = "closed_days", nullable = false, length = 20)
    private String closedDays = "7";

    @Column(name = "open_start", nullable = false)
    private LocalTime openStart = LocalTime.of(9, 0);

    @Column(name = "open_end", nullable = false)
    private LocalTime openEnd = LocalTime.of(13, 0);

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes = 30;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "updated_by")
    private UUID updatedBy;
}
