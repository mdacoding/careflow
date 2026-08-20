package de.careflow.service;

import de.careflow.domain.AuditEventEntity;
import de.careflow.domain.AuditEventRepository;
import de.careflow.security.Staff;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public void record(Staff staff, String action, String entityType, String entityId, String detail) {
        AuditEventEntity event = new AuditEventEntity();
        event.setActor(staff.displayName());
        event.setActorRole(staff.role());
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDetail(detail);
        repository.save(event);
    }

    public List<AuditEventEntity> recent() {
        return repository.findTop40ByOrderByCreatedAtDesc();
    }
}
