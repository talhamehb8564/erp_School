package com.erpschool.admission;

import com.erpschool.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

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
}
