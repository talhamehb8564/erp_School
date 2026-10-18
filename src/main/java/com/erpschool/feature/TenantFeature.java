package com.erpschool.feature;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tenant_features")
@IdClass(TenantFeature.Key.class)
public class TenantFeature {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SchoolFeature feature;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private UUID tenantId;
        private SchoolFeature feature;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return java.util.Objects.equals(tenantId, other.tenantId) && feature == other.feature;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tenantId, feature);
        }
    }
}
