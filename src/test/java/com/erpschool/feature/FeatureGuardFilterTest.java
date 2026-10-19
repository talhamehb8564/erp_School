package com.erpschool.feature;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureGuardFilterTest {

    @Test
    void mapsModulePathsAndLeavesIdentityAlone() {
        assertThat(FeatureGuardFilter.featureFor("/api/v1/fees/challans")).isEqualTo(SchoolFeature.FEES);
        assertThat(FeatureGuardFilter.featureFor("/api/v1/attendance/teachers")).isEqualTo(SchoolFeature.TEACHER_ATTENDANCE);
        assertThat(FeatureGuardFilter.featureFor("/api/v1/attendance/lectures/1")).isEqualTo(SchoolFeature.ATTENDANCE);
        assertThat(FeatureGuardFilter.featureFor("/api/v1/online-exams/1")).isEqualTo(SchoolFeature.ONLINE_EXAMS);
        assertThat(FeatureGuardFilter.featureFor("/api/v1/exams/sessions")).isEqualTo(SchoolFeature.RESULTS);
        assertThat(FeatureGuardFilter.featureFor("/api/v1/portals/parent")).isEqualTo(SchoolFeature.PARENT_PORTAL);
        assertThat(FeatureGuardFilter.featureFor("/api/v1/features")).isNull();
        assertThat(FeatureGuardFilter.featureFor("/api/v1/auth/login")).isNull();
        assertThat(FeatureGuardFilter.featureFor("/api/v1/tenants/me")).isNull();
    }
}
