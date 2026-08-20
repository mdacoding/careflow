package de.careflow.cds;

import java.util.List;

public class CdsBlockException extends RuntimeException {

    private final List<CdsEngine.Finding> findings;

    public CdsBlockException(List<CdsEngine.Finding> findings) {
        super("Verordnung durch AMTS-Regelwerk gesperrt");
        this.findings = findings;
    }

    public List<CdsEngine.Finding> getFindings() {
        return findings;
    }
}
