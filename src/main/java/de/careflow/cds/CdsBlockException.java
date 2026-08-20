package de.careflow.cds;

import java.util.List;

public class CdsBlockException extends RuntimeException {

    private final List<CdsEngine.Finding> findings;

    public CdsBlockException(List<CdsEngine.Finding> findings) {
        super("AMTS: Verordnung gesperrt");
        this.findings = findings;
    }

    public List<CdsEngine.Finding> getFindings() {
        return findings;
    }
}
