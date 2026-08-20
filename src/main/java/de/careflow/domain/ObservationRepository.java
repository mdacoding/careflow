package de.careflow.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObservationRepository extends JpaRepository<ObservationEntity, String> {
    List<ObservationEntity> findByOrderIdOrderBySortOrderAsc(String orderId);

    List<ObservationEntity> findByOrderIdIn(List<String> orderIds);
}
