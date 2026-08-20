package de.careflow.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Hl7MessageRepository extends JpaRepository<Hl7MessageEntity, String> {
    List<Hl7MessageEntity> findByOrderIdOrderByCreatedAtAsc(String orderId);

    List<Hl7MessageEntity> findAllByOrderByCreatedAtDesc();
}
