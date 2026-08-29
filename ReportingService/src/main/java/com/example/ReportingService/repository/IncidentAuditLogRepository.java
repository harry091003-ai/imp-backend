package com.example.ReportingService.repository;

import com.example.ReportingService.entity.IncidentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentAuditLogRepository extends JpaRepository<IncidentAuditLog, UUID> {

    List<IncidentAuditLog> findByIncidentIdOrderByTimestampAsc(UUID incidentId);
}
