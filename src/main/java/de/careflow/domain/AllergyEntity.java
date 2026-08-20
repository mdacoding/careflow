package de.careflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "allergies")
public class AllergyEntity {

    @Id
    private String id;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(nullable = false)
    private String substance;

    @Column(name = "atc_prefix")
    private String atcPrefix;

    private String snomed;

    @Column(nullable = false)
    private String criticality;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getSubstance() {
        return substance;
    }

    public void setSubstance(String substance) {
        this.substance = substance;
    }

    public String getAtcPrefix() {
        return atcPrefix;
    }

    public void setAtcPrefix(String atcPrefix) {
        this.atcPrefix = atcPrefix;
    }

    public String getSnomed() {
        return snomed;
    }

    public void setSnomed(String snomed) {
        this.snomed = snomed;
    }

    public String getCriticality() {
        return criticality;
    }

    public void setCriticality(String criticality) {
        this.criticality = criticality;
    }
}
