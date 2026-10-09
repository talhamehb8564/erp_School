package com.erpschool.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    @Test
    void temporaryPasswordMeetsPolicy() {
        String password = PasswordGenerator.temporaryPassword();
        assertThat(password.length()).isGreaterThanOrEqualTo(10);
        assertThat(password).matches(".*[A-Z].*");
        assertThat(password).matches(".*[a-z].*");
        assertThat(password).matches(".*\\d.*");
        assertThat(password).matches(".*[^A-Za-z0-9].*");
    }
}
