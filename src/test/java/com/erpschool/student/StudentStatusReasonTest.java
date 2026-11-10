package com.erpschool.student;

import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.service.StudentService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudentStatusReasonTest {

    @Test
    void reasonsMapOntoExistingStatusesOnly() {
        assertThat(StudentService.statusForReason("GRADUATED")).isEqualTo(StudentStatus.GRADUATED);
        assertThat(StudentService.statusForReason("GRADE_10_COMPLETE")).isEqualTo(StudentStatus.GRADUATED);
        assertThat(StudentService.statusForReason("LEFT_SCHOOL")).isEqualTo(StudentStatus.LEFT_SCHOOL);
        assertThat(StudentService.statusForReason("TRANSFERRED")).isEqualTo(StudentStatus.LEFT_SCHOOL);
        assertThat(StudentService.statusForReason("UNPAID_FEES")).isEqualTo(StudentStatus.INACTIVE);
        assertThat(StudentService.statusForReason("PARENT_REQUEST")).isEqualTo(StudentStatus.INACTIVE);
        assertThat(StudentService.statusForReason("OTHER")).isEqualTo(StudentStatus.INACTIVE);
        assertThat(StudentService.statusForReason(null)).isEqualTo(StudentStatus.INACTIVE);
    }

    @Test
    void otherRequiresANote() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> StudentService.parseReason("OTHER", "  "))
                .isInstanceOf(com.erpschool.common.exception.BusinessException.class)
                .hasMessageContaining("Other");
        StudentService.StatusReason parsed = StudentService.parseReason("OTHER", "Moved abroad");
        org.assertj.core.api.Assertions.assertThat(parsed.code()).isEqualTo("OTHER");
        org.assertj.core.api.Assertions.assertThat(parsed.note()).isEqualTo("Moved abroad");
        org.assertj.core.api.Assertions.assertThat(parsed.status()).isEqualTo(StudentStatus.INACTIVE);
    }
}
