package de.careflow.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogTest {

    @Test
    void bbcRpOverlapsBloodCountAndCrp() {
        assertThat(Catalog.labConflicts("BBCRP", "BB")).isTrue();
        assertThat(Catalog.labConflicts("CRP", "BBCRP")).isTrue();
        assertThat(Catalog.labConflicts("BB", "CRP")).isFalse();
        assertThat(Catalog.labConflicts("TROP", "BB")).isFalse();
    }
}
