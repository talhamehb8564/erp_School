package com.erpschool.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void randomTokensAreUniqueAndHex() {
        String a = TokenHasher.randomToken();
        String b = TokenHasher.randomToken();
        assertThat(a).hasSize(64).matches("[0-9a-f]+");
        assertThat(b).isNotEqualTo(a);
    }

    @Test
    void sha256IsStable() {
        String hash = TokenHasher.sha256("refresh-token");
        assertThat(hash).isEqualTo(TokenHasher.sha256("refresh-token"));
        assertThat(hash).hasSize(64);
        assertThat(hash).isNotEqualTo(TokenHasher.sha256("other"));
    }
}
