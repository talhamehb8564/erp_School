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
        assertThat(StudentService.statusForReason("OTHER")).isEqualTo(StudentStatus.INACTIVE);
        assertThat(StudentService.statusForReason(null)).isEqualTo(StudentStatus.INACTIVE);
    }
}
