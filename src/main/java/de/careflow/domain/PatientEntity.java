package de.careflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patients")
public class PatientEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String mrn;

    @Column(name = "given_name", nullable = false)
    private String givenName;

    @Column(name = "family_name", nullable = false)
    private String familyName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, length = 1)
    private String sex;

    @Column(nullable = false)
    private String ward;

    @Column(nullable = false)
    private String bed;

    @Column(nullable = false)
    private String department;

    @Column(name = "chief_complaint")
    private String chiefComplaint;

    @Column(name = "working_diagnosis")
    private String workingDiagnosis;

    @Column(name = "demo_star", nullable = false)
    private boolean demoStar;

    @Column(nullable = false)
    private String acuity;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String displayName() {
        return familyName + ", " + givenName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMrn() {
        return mrn;
    }

    public void setMrn(String mrn) {
        this.mrn = mrn;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public String getBed() {
        return bed;
    }

    public void setBed(String bed) {
        this.bed = bed;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public void setChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
    }

    public String getWorkingDiagnosis() {
        return workingDiagnosis;
    }

    public void setWorkingDiagnosis(String workingDiagnosis) {
        this.workingDiagnosis = workingDiagnosis;
    }

    public boolean isDemoStar() {
        return demoStar;
    }

    public void setDemoStar(boolean demoStar) {
        this.demoStar = demoStar;
    }

    public String getAcuity() {
        return acuity;
    }

    public void setAcuity(String acuity) {
        this.acuity = acuity;
    }
}
