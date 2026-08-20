package de.careflow.cds;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CdsEngineTest {

    private final CdsEngine engine = new CdsEngine();

    @Test
    void blocksAmoxicillinOnPenicillinAllergy() {
        List<CdsEngine.Finding> findings = engine.evaluate(new CdsEngine.Request(
                "AMOX",
                "J01CA04",
                "Amoxicillin",
                List.of(new CdsEngine.Allergy("Penicillin", "J01C")),
                List.of(),
                "Verdacht auf Pneumonie",
                null));

        assertThat(findings).anyMatch(finding -> finding.blocking() && "ALLERGY_MATCH".equals(finding.ruleId()));
    }

    @Test
    void warnsOnCephalosporinCrossAndDuplicateAtc() {
        List<CdsEngine.Finding> findings = engine.evaluate(new CdsEngine.Request(
                "CEFU",
                "J01DC02",
                "Cefuroxim",
                List.of(new CdsEngine.Allergy("Penicillin", "J01C")),
                List.of(new CdsEngine.ActiveMed("CEFU", "J01DC02", "Cefuroxim")),
                "Pneumonie",
                null));

        assertThat(findings)
                .extracting(CdsEngine.Finding::ruleId)
                .contains("CEPHALOSPORIN_CROSS", "DUPLICATE_ATC");
        assertThat(findings).noneMatch(CdsEngine.Finding::blocking);
    }

    @Test
    void warnsNsaidInHeartFailure() {
        List<CdsEngine.Finding> findings = engine.evaluate(new CdsEngine.Request(
                "IBUP",
                "M01AE01",
                "Ibuprofen",
                List.of(),
                List.of(),
                "Herzinsuffizienz NYHA III",
                1.7));

        assertThat(findings)
                .extracting(CdsEngine.Finding::ruleId)
                .contains("NSAID_HEART_FAILURE", "RENAL_NSAID");
    }
}
