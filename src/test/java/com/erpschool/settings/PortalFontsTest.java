package com.erpschool.settings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortalFontsTest {

    @Test
    void keepsRequestedPortalFonts() {
        assertThat(PortalFonts.sanitize("Times New Roman")).isEqualTo("Times New Roman");
        assertThat(PortalFonts.sanitize("Poppins")).isEqualTo("Poppins");
        assertThat(PortalFonts.sanitize("Open Sans")).isEqualTo("Open Sans");
        assertThat(PortalFonts.sanitize("Montserrat")).isEqualTo("Montserrat");
    }

    @Test
    void unknownFontDoesNotStoreGarbage() {
        assertThat(PortalFonts.sanitize("Comic Sans")).isEqualTo(PortalFonts.DEFAULT);
        assertThat(PortalFonts.sanitize(" ")).isEqualTo(PortalFonts.DEFAULT);
        assertThat(PortalFonts.sanitize(null)).isEqualTo(PortalFonts.DEFAULT);
    }
}
