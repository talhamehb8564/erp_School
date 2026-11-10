package com.erpschool.admission;

import com.erpschool.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdmissionServiceTest {

    @Test
    void cnicMustBeThirteenDigits() {
        assertThat(AdmissionService.normalizeCnic("35202-1234567-1")).isEqualTo("3520212345671");
        assertThatThrownBy(() -> AdmissionService.normalizeCnic("12345"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("13");
    }

    @Test
    void phoneMustBeTenToFifteenDigits() {
        assertThat(AdmissionService.normalizePhone("0300-1234567")).isEqualTo("03001234567");
        assertThatThrownBy(() -> AdmissionService.normalizePhone("123"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void closedDaysDefaultToSundayAndSerialize() {
        assertThat(AdmissionService.parseClosedDays(null)).containsExactly(7);
        assertThat(AdmissionService.parseClosedDays("7")).containsExactly(7);
        assertThat(AdmissionService.parseClosedDays("6,7")).containsExactly(6, 7);
        assertThat(AdmissionService.writeClosedDays(AdmissionService.parseClosedDays(java.util.List.of(7, 1))))
                .isEqualTo("1,7");
    }

    @Test
    void slotsAreGeneratedFromOpenHoursAndDuration() {
        assertThat(AdmissionService.slotStarts(LocalTime.of(9, 0), LocalTime.of(13, 0), 30))
                .containsExactly(
                        LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30),
                        LocalTime.of(11, 0), LocalTime.of(11, 30), LocalTime.of(12, 0), LocalTime.of(12, 30));
        assertThat(AdmissionService.slotStarts(LocalTime.of(16, 0), LocalTime.of(19, 0), 30))
                .containsExactly(
                        LocalTime.of(16, 0), LocalTime.of(16, 30), LocalTime.of(17, 0),
                        LocalTime.of(17, 30), LocalTime.of(18, 0), LocalTime.of(18, 30));
        assertThat(AdmissionService.slotStarts(LocalTime.of(8, 0), LocalTime.of(15, 0), 60))
                .containsExactly(
                        LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0),
                        LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(14, 0));
    }

    @Test
    void duplicateCnicMessageUsesNameNotTheNumber() {
        AdmissionApplication pending = new AdmissionApplication();
        pending.setStudentName("Ali Ahmed");
        pending.setCnic("3520212345671");
        pending.setStatus(AdmissionApplicationStatus.SUBMITTED);
        BusinessException pendingEx = AdmissionService.duplicateCnicException(pending, "Ali Ahmed");
        assertThat(pendingEx.getMessage()).contains("Ali Ahmed");
        assertThat(pendingEx.getMessage()).contains("already been submitted");
        assertThat(pendingEx.getMessage()).doesNotContain("35202");
        assertThat(pendingEx.getErrorCode()).isEqualTo("ADMISSION_EXISTS");

        AdmissionApplication accepted = new AdmissionApplication();
        accepted.setStudentName("Ali Ahmed");
        accepted.setCnic("3520212345671");
        accepted.setStatus(AdmissionApplicationStatus.ACCEPTED);
        BusinessException acceptedEx = AdmissionService.duplicateCnicException(accepted, "Ali Ahmed");
        assertThat(acceptedEx.getErrorCode()).isEqualTo("ADMISSION_EXISTS_ACCEPTED");
        assertThat(acceptedEx.getMessage()).contains("already been accepted");
        assertThat(acceptedEx.getMessage()).doesNotContain("3520212345671");
    }
}
