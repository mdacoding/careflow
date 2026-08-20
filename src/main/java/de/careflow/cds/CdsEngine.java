package de.careflow.cds;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CdsEngine {

    public record Allergy(String substance, String atcPrefix) {
    }

    public record ActiveMed(String catalogCode, String atc, String display) {
    }

    public record Request(
            String catalogCode,
            String atc,
            String display,
            List<Allergy> allergies,
            List<ActiveMed> activeMeds,
            String diagnosis,
            Double latestCreatinine,
            Integer ageYears,
            String sex) {
    }

    public record Finding(String ruleId, String severity, String title, String message) {
        public boolean blocking() {
            return "BLOCK".equals(severity);
        }

        int rank() {
            return blocking() ? 0 : "WARNING".equals(severity) ? 1 : 2;
        }
    }

    public List<Finding> evaluate(Request request) {
        AtcCode atc = AtcCode.parse(request.atc());
        Double egfr = EgfrCalculator.ckdEpi2021(request.latestCreatinine(), request.ageYears(), request.sex());
        List<Finding> raw = new ArrayList<>();
        raw.addAll(allergyAndCross(request, atc));
        raw.addAll(duplicateAtc(request, atc));
        raw.addAll(nsaidHeartFailure(request, atc));
        raw.addAll(renal(request, atc, egfr));
        Map<String, Finding> unique = new LinkedHashMap<>();
        for (Finding finding : raw) {
            unique.putIfAbsent(finding.ruleId(), finding);
        }
        return unique.values().stream().sorted(Comparator.comparingInt(Finding::rank)).toList();
    }

    private List<Finding> allergyAndCross(Request request, AtcCode atc) {
        List<Finding> findings = new ArrayList<>();
        String display = safe(request.display());
        for (Allergy allergy : request.allergies()) {
            AtcCode prefix = AtcCode.parse(allergy.atcPrefix());
            boolean atcHit = atc.coveredBy(prefix);
            boolean nameHit = prefix.isBlank() && (contains(display, allergy.substance())
                    || contains(allergy.substance(), display));
            if (atcHit || nameHit) {
                findings.add(new Finding(
                        "ALLERGY_MATCH",
                        "BLOCK",
                        "AMTS: Allergie " + allergy.substance(),
                        "Verordnung %s (ATC %s) ist bei dokumentierter Allergie gegen %s gesperrt.".formatted(
                                request.display(), atc.value(), allergy.substance())));
            } else if (atc.cephalosporin() && prefix.penicillinClass()) {
                findings.add(new Finding(
                        "CEPHALOSPORIN_CROSS",
                        "WARNING",
                        "AMTS: Kreuzallergie β-Laktam",
                        "Cephalosporin (ATC J01D) nach Penicillin-Allergie (J01C): Kreuzreaktion selten, klinisch abwägen."));
            }
        }
        return findings;
    }

    private List<Finding> duplicateAtc(Request request, AtcCode atc) {
        List<Finding> findings = new ArrayList<>();
        for (ActiveMed active : request.activeMeds()) {
            AtcCode existing = AtcCode.parse(active.atc());
            if (atc.sameChemicalGroup(existing)) {
                findings.add(new Finding(
                        "DUPLICATE_ATC",
                        "WARNING",
                        "AMTS: Doppelverordnung",
                        "%s ist bereits aktiv (chemische ATC-Gruppe %s).".formatted(
                                active.display(), existing.chemicalGroup())));
            }
        }
        return findings;
    }

    private List<Finding> nsaidHeartFailure(Request request, AtcCode atc) {
        if (atc.nsaid() && contains(request.diagnosis(), "Herzinsuffizienz")) {
            return List.of(new Finding(
                    "NSAID_HEART_FAILURE",
                    "WARNING",
                    "AMTS: NSAR bei Herzinsuffizienz",
                    "NSAR (ATC M01A) können die Herzinsuffizienz verschlechtern und die Nierenfunktion belasten."));
        }
        return List.of();
    }

    private List<Finding> renal(Request request, AtcCode atc, Double egfr) {
        List<Finding> findings = new ArrayList<>();
        if (egfr == null) {
            return findings;
        }
        if (atc.nsaid() && egfr < 30) {
            findings.add(new Finding(
                    "RENAL_NSAID",
                    "BLOCK",
                    "AMTS: NSAR bei eGFR < 30",
                    "CKD-EPI eGFR %.0f ml/min/1,73 m²: NSAR ist kontraindiziert.".formatted(egfr)));
        } else if (atc.nsaid() && egfr < 60) {
            findings.add(new Finding(
                    "RENAL_NSAID",
                    "WARNING",
                    "AMTS: NSAR bei eingeschränkter Niere",
                    "CKD-EPI eGFR %.0f ml/min/1,73 m²: NSAR nur mit strenger Indikation.".formatted(egfr)));
        }
        if (atc.aceInhibitor() && egfr < 30) {
            findings.add(new Finding(
                    "RENAL_ACEI",
                    "WARNING",
                    "AMTS: ACE-Hemmer bei eGFR < 30",
                    "CKD-EPI eGFR %.0f ml/min/1,73 m²: ACE-Hemmer (C09A) nur unter Kontrolle von Krea/K+.".formatted(egfr)));
        }
        return findings;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean contains(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) {
            return false;
        }
        return haystack.toLowerCase(Locale.GERMAN).contains(needle.toLowerCase(Locale.GERMAN));
    }
}
