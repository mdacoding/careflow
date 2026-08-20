package de.careflow.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AllergyRepository extends JpaRepository<AllergyEntity, String> {
    List<AllergyEntity> findByPatientId(String patientId);
}
