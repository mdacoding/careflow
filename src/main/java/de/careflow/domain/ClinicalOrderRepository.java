package de.careflow.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ClinicalOrderRepository extends JpaRepository<ClinicalOrderEntity, String> {
    List<ClinicalOrderEntity> findByPatientIdOrderByOrderedAtDesc(String patientId);

    List<ClinicalOrderEntity> findByKindAndStatusInOrderByOrderedAtAsc(OrderKind kind, Collection<OrderStatus> statuses);

    List<ClinicalOrderEntity> findByPatientIdAndKindAndStatusIn(String patientId, OrderKind kind, Collection<OrderStatus> statuses);

    List<ClinicalOrderEntity> findByPatientIdIn(Collection<String> patientIds);
}
