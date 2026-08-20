package de.careflow.lab;

import java.math.BigDecimal;
import java.util.Map;

/**
 * HL7-Tabelle 0078: N, L, H, LL, HH. Panic-Grenzen je LOINC, sonst 5× ULN / 0,2× LLN.
 */
public final class ReferenceRangeInterpreter {

    private static final Map<String, Panic> PANIC = Map.of(
            "1988-5", new Panic(null, new BigDecimal("50")),
            "6690-2", new Panic(new BigDecimal("1.0"), new BigDecimal("25")),
            "718-7", new Panic(new BigDecimal("7.0"), null),
            "10839-9", new Panic(null, new BigDecimal("0.5")),
            "2160-0", new Panic(null, new BigDecimal("4.0")));

    private record Panic(BigDecimal low, BigDecimal high) {
    }

    private ReferenceRangeInterpreter() {
    }

    public static String interpret(String loinc, BigDecimal value, BigDecimal refLow, BigDecimal refHigh) {
        if (value == null) {
            return "N";
        }
        Panic panic = PANIC.getOrDefault(loinc, new Panic(null, null));
        if (panic.high() != null && value.compareTo(panic.high()) > 0) {
            return "HH";
        }
        if (panic.low() != null && value.compareTo(panic.low()) < 0) {
            return "LL";
        }
        if (refHigh != null && value.compareTo(refHigh) > 0) {
            if (value.compareTo(refHigh.multiply(new BigDecimal("5"))) > 0) {
                return "HH";
            }
            return "H";
        }
        if (refLow != null && value.compareTo(refLow) < 0) {
            if (refLow.compareTo(BigDecimal.ZERO) > 0
                    && value.compareTo(refLow.multiply(new BigDecimal("0.2"))) < 0) {
                return "LL";
            }
            return "L";
        }
        return "N";
    }
}
