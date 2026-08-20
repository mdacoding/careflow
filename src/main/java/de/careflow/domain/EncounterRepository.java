package de.careflow.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EncounterRepository extends JpaRepository<EncounterEntity, String> {
    Optional<EncounterEntity> findFirstByPatientIdAndStatusOrderByAdmittedAtDesc(String patientId, String status);
}
