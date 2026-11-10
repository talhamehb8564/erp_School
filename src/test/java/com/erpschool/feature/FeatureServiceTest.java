package com.erpschool.feature;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureServiceTest {

    @Test
    void parseAcceptsWireAndEnumNames() {
        assertThat(FeatureService.parse("fees")).isEqualTo(SchoolFeature.FEES);
        assertThat(FeatureService.parse("PARENT_PORTAL")).isEqualTo(SchoolFeature.PARENT_PORTAL);
        assertThat(FeatureService.parse("teacher-attendance")).isEqualTo(SchoolFeature.TEACHER_ATTENDANCE);
        assertThat(FeatureService.parse("nope")).isNull();
    }
}
