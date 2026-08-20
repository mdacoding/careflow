package de.careflow.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<PatientEntity, String> {
    Optional<PatientEntity> findByMrn(String mrn);

    List<PatientEntity> findAllByWardOrderByBedAsc(String ward);

    List<PatientEntity> findByIdIn(Collection<String> ids);
}
