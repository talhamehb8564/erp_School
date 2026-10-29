package com.erpschool.exam.online;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "online_exam_sections")
@IdClass(OnlineExamSection.Key.class)
public class OnlineExamSection {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private UUID examId;
        private UUID sectionId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(examId, key.examId) && Objects.equals(sectionId, key.sectionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(examId, sectionId);
        }
    }
}
