package de.careflow.cds;

import java.util.Locale;

/**
 * WHO-ATC-Hierarchie: 1 anatomisch, 3 therapeutisch, 4 pharmakologisch, 5 chemische Untergruppe, 7 Wirkstoff.
 */
public final class AtcCode {

    private final String value;

    private AtcCode(String value) {
        this.value = value;
    }

    public static AtcCode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new AtcCode("");
        }
        return new AtcCode(raw.trim().toUpperCase(Locale.ROOT).replace(" ", ""));
    }

    public boolean isBlank() {
        return value.isBlank();
    }

    public String value() {
        return value;
    }

    public String chemicalGroup() {
        return value.length() >= 5 ? value.substring(0, 5) : value;
    }

    public boolean coveredBy(AtcCode prefix) {
        return !isBlank() && !prefix.isBlank() && value.startsWith(prefix.value);
    }

    public boolean sameChemicalGroup(AtcCode other) {
        if (chemicalGroup().length() < 5 || other.chemicalGroup().length() < 5) {
            return value.equals(other.value) && !value.isBlank();
        }
        return chemicalGroup().equals(other.chemicalGroup());
    }

    public boolean penicillinClass() {
        return value.startsWith("J01C");
    }

    public boolean cephalosporin() {
        return value.startsWith("J01D");
    }

    public boolean nsaid() {
        return value.startsWith("M01A");
    }

    public boolean aceInhibitor() {
        return value.startsWith("C09A");
    }
}
