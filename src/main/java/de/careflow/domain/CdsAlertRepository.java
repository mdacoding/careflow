package de.careflow.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CdsAlertRepository extends JpaRepository<CdsAlertEntity, String> {
    List<CdsAlertEntity> findByOrderId(String orderId);

    List<CdsAlertEntity> findByPatientIdOrderByIdDesc(String patientId);
}
