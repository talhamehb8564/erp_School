package com.erpschool.common;

import com.erpschool.common.util.Instants;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstantsTest {

    @Test
    void parsesIsoAndDatetimeLocal() {
        Instant iso = Instants.parse("2026-10-09T12:00:00Z");
        assertThat(iso).isEqualTo(Instant.parse("2026-10-09T12:00:00Z"));
        assertThat(Instants.parse("2026-10-09T12:00")).isNotNull();
        assertThat(Instants.parse(" ")).isNull();
        assertThat(Instants.parse(null)).isNull();
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> Instants.parse("not-a-date"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
