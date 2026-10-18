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
    void slotsAreGeneratedFromOpenHoursAndDuration() {
        assertThat(AdmissionService.slotStarts(LocalTime.of(9, 0), LocalTime.of(13, 0), 30))
                .containsExactly(
                        LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30),
                        LocalTime.of(11, 0), LocalTime.of(11, 30), LocalTime.of(12, 0), LocalTime.of(12, 30));
        assertThat(AdmissionService.slotStarts(LocalTime.of(16, 0), LocalTime.of(19, 0), 30))
                .containsExactly(
                        LocalTime.of(16, 0), LocalTime.of(16, 30), LocalTime.of(17, 0),
                        LocalTime.of(17, 30), LocalTime.of(18, 0), LocalTime.of(18, 30));
    }
}
