package de.careflow.cds;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtcCodeTest {

    @Test
    void penicillinPrefixCoversAmoxicillin() {
        assertThat(AtcCode.parse("J01CA04").coveredBy(AtcCode.parse("J01C"))).isTrue();
    }

    @Test
    void chemicalGroupTreatsSameSubclassAsDuplicate() {
        assertThat(AtcCode.parse("J01CA04").sameChemicalGroup(AtcCode.parse("J01CA01"))).isTrue();
        assertThat(AtcCode.parse("J01CA04").sameChemicalGroup(AtcCode.parse("J01DC02"))).isFalse();
    }
}
