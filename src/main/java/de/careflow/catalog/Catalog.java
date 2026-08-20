package de.careflow.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class Catalog {

    public record LabItem(String code, String display, String loincPanel, String description) {
    }

    public record MedItem(String code, String display, String atc, String pzn, String dose, String route) {
    }

    public static final List<LabItem> LABS = List.of(
            new LabItem("BBCRP", "Blutbild + CRP", "57021-8", "Notaufnahme-Panel: Leukozyten, Hb, Thrombozyten, CRP"),
            new LabItem("BB", "Kleines Blutbild", "58410-2", "Leukozyten, Hämoglobin, Thrombozyten"),
            new LabItem("CRP", "C-reaktives Protein", "1988-5", "Entzündungsparameter"),
            new LabItem("TROP", "Troponin I", "10839-9", "Kardialer Marker"),
            new LabItem("KREA", "Kreatinin", "2160-0", "Nierenfunktion"),
            new LabItem("BGA", "Blutgasanalyse", "24336-0", "pH, pO2, pCO2"));

    public static final List<MedItem> MEDS = List.of(
            new MedItem("AMOX", "Amoxicillin", "J01CA04", "02564573", "1 g", "PO"),
            new MedItem("CEFU", "Cefuroxim", "J01DC02", "03819422", "500 mg", "PO"),
            new MedItem("PARA", "Paracetamol", "N02BE01", "01125512", "1 g", "PO"),
            new MedItem("IBUP", "Ibuprofen", "M01AE01", "00998701", "400 mg", "PO"),
            new MedItem("RAMI", "Ramipril", "C09AA05", "01543210", "5 mg", "PO"),
            new MedItem("TORA", "Torasemid", "C03CA04", "02219887", "10 mg", "PO"));

    private Catalog() {
    }

    public static Optional<LabItem> lab(String code) {
        return LABS.stream().filter(item -> item.code().equals(code)).findFirst();
    }

    public static Optional<MedItem> med(String code) {
        return MEDS.stream().filter(item -> item.code().equals(code)).findFirst();
    }

    /**
     * Offene Aufträge derselben Messung oder eines überlappenden Panels (BBCRP ⊃ BB, CRP) gelten als Duplikat.
     */
    public static boolean labConflicts(String requested, String existing) {
        Set<String> requestedParts = new HashSet<>(labParts(requested));
        requestedParts.retainAll(labParts(existing));
        return !requestedParts.isEmpty();
    }

    private static Set<String> labParts(String code) {
        return switch (code) {
            case "BBCRP" -> Set.of("BB", "CRP");
            case "BB" -> Set.of("BB");
            case "CRP" -> Set.of("CRP");
            default -> Set.of(code);
        };
    }
}
