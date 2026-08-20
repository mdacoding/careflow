package de.careflow.cds;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CdsEngineTest {

    private final CdsEngine engine = new CdsEngine();

    @Test
    void blocksAmoxicillinOnPenicillinAllergyViaAtcHierarchy() {
        List<CdsEngine.Finding> findings = engine.evaluate(amoxRequest(
                List.of(new CdsEngine.Allergy("Penicillin", "J01C")),
                List.of(),
                "Verdacht auf Pneumonie",
                null,
                67,
                "F"));

        assertThat(findings.getFirst().ruleId()).isEqualTo("ALLERGY_MATCH");
        assertThat(findings.getFirst().blocking()).isTrue();
        assertThat(findings.getFirst().title()).contains("AMTS");
    }

    @Test
    void warnsOnCephalosporinCrossAndDuplicateChemicalGroup() {
        List<CdsEngine.Finding> findings = engine.evaluate(new CdsEngine.Request(
                "CEFU",
                "J01DC02",
                "Cefuroxim",
                List.of(new CdsEngine.Allergy("Penicillin", "J01C")),
                List.of(new CdsEngine.ActiveMed("CEFU", "J01DC02", "Cefuroxim")),
                "Pneumonie",
                null,
                67,
                "F"));

        assertThat(findings)
                .extracting(CdsEngine.Finding::ruleId)
                .contains("CEPHALOSPORIN_CROSS", "DUPLICATE_ATC");
        assertThat(findings).noneMatch(CdsEngine.Finding::blocking);
    }

    @Test
    void warnsNsaidInHeartFailureAndReducedEgfr() {
        List<CdsEngine.Finding> findings = engine.evaluate(new CdsEngine.Request(
                "IBUP",
                "M01AE01",
                "Ibuprofen",
                List.of(),
                List.of(),
                "Herzinsuffizienz NYHA III",
                1.7,
                79,
                "M"));

        assertThat(findings)
                .extracting(CdsEngine.Finding::ruleId)
                .contains("NSAID_HEART_FAILURE", "RENAL_NSAID");
        assertThat(findings).filteredOn(f -> "RENAL_NSAID".equals(f.ruleId()))
                .first()
                .extracting(CdsEngine.Finding::severity)
                .isEqualTo("WARNING");
    }

    @Test
    void blocksNsaidWhenCkdEpiBelow30() {
        List<CdsEngine.Finding> findings = engine.evaluate(new CdsEngine.Request(
                "IBUP",
                "M01AE01",
                "Ibuprofen",
                List.of(),
                List.of(),
                "Herzinsuffizienz NYHA III",
                3.2,
                80,
                "M"));

        assertThat(findings).anyMatch(f -> "RENAL_NSAID".equals(f.ruleId()) && f.blocking());
    }

    private static CdsEngine.Request amoxRequest(
            List<CdsEngine.Allergy> allergies,
            List<CdsEngine.ActiveMed> meds,
            String diagnosis,
            Double crea,
            Integer age,
            String sex) {
        return new CdsEngine.Request("AMOX", "J01CA04", "Amoxicillin", allergies, meds, diagnosis, crea, age, sex);
    }
}
