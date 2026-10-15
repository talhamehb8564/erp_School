package com.erpschool.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BranchIsolationSeederTest {

    @Test
    void generatedUsernamesAreDetectedWithoutTreatingChosenLoginsAsGenerated() {
        assertThat(BranchIsolationSeeder.looksGeneratedUsername(null, "GVS")).isTrue();
        assertThat(BranchIsolationSeeder.looksGeneratedUsername("GVS-ST-0001", "GVS")).isTrue();
        assertThat(BranchIsolationSeeder.looksGeneratedUsername("gvs-sa-0001", "GVS")).isTrue();
        assertThat(BranchIsolationSeeder.looksGeneratedUsername("ali.raza", "GVS")).isFalse();
        assertThat(BranchIsolationSeeder.looksGeneratedUsername("3520112345671", "GVS")).isFalse();
    }
}
