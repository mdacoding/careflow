package de.careflow.cds;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            Double latestCreatinine) {
    }

    public record Finding(String ruleId, String severity, String title, String message) {
        public boolean blocking() {
            return "BLOCK".equals(severity);
        }
    }

    public List<Finding> evaluate(Request request) {
        List<Finding> findings = new ArrayList<>();
        String atc = safe(request.atc());
        String display = safe(request.display());
        String diagnosis = safe(request.diagnosis());

        for (Allergy allergy : request.allergies()) {
            boolean prefixHit = allergy.atcPrefix() != null
                    && !allergy.atcPrefix().isBlank()
                    && atc.startsWith(allergy.atcPrefix());
            boolean nameHit = contains(display, allergy.substance()) || contains(allergy.substance(), display);
            if (prefixHit || nameHit) {
                findings.add(new Finding(
                        "ALLERGY_MATCH",
                        "BLOCK",
                        "Allergie: " + allergy.substance(),
                        "Verordnung von %s ist bei dokumentierter Allergie gegen %s gesperrt.".formatted(
                                request.display(), allergy.substance())));
            } else if (atc.startsWith("J01D") && "J01C".equals(allergy.atcPrefix())) {
                findings.add(new Finding(
                        "CEPHALOSPORIN_CROSS",
                        "WARNING",
                        "Mögliche Kreuzallergie",
                        "Cephalosporin nach Penicillin-Allergie: Kreuzreaktion selten, klinisch abwägen."));
            }
        }

        for (ActiveMed active : request.activeMeds()) {
            if (active.atc() != null && !active.atc().isBlank() && active.atc().equals(atc)) {
                findings.add(new Finding(
                        "DUPLICATE_ATC",
                        "WARNING",
                        "Doppelverordnung",
                        "%s ist bereits aktiv (ATC %s).".formatted(active.display(), active.atc())));
            }
        }

        if (atc.startsWith("M01A") && contains(diagnosis, "Herzinsuffizienz")) {
            findings.add(new Finding(
                    "NSAID_HEART_FAILURE",
                    "WARNING",
                    "NSAR bei Herzinsuffizienz",
                    "Ibuprofen/NSAR können die Herzinsuffizienz verschlechtern und die Nierenfunktion belasten."));
        }

        if (request.latestCreatinine() != null && request.latestCreatinine() >= 1.5 && atc.startsWith("M01A")) {
            findings.add(new Finding(
                    "RENAL_NSAID",
                    "WARNING",
                    "NSAR bei eingeschränkter Niere",
                    "Kreatinin %.1f mg/dl: NSAR nur mit strenger Indikation.".formatted(request.latestCreatinine())));
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
