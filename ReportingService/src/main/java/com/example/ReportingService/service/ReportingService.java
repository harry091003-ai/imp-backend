package com.example.ReportingService.service;

import com.example.ReportingService.dto.*;
import com.example.ReportingService.entity.IncidentAuditLog;
import com.example.ReportingService.entity.IncidentReport;
import com.example.ReportingService.repository.IncidentAuditLogRepository;
import com.example.ReportingService.repository.IncidentReportRepository;
import com.project.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final IncidentReportRepository incidentReportRepository;
    private final IncidentAuditLogRepository auditLogRepository;

    // 1. Get Incident by ID
    public IncidentReportResponseDTO getIncident(UUID incidentId) {

        IncidentReport incident = incidentReportRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("INCIDENT_REPORT_NOT_FOUND",
                        "No report found for incident: " + incidentId));

        return mapToDTO(incident);
    }

    // 2. List with filters
    public Page<IncidentReportResponseDTO> getIncidents(String status, String severity, Pageable pageable) {

        Page<IncidentReport> incidents;
Pageable Storedpageable= PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("createdAt").ascending());

if (status != null && severity != null) {
            incidents = incidentReportRepository.findByStatusAndSeverity(status, severity,Storedpageable);
        } else if (status != null) {
            incidents = incidentReportRepository.findByStatus(status,Storedpageable);
        } else {
            incidents = incidentReportRepository.findAll(Storedpageable);
        }

        return incidents.map(this::mapToDTO);
    }

    // 3. Audit history
    public List<IncidentAuditDTO> getAuditLogs(UUID incidentId) {

       return  auditLogRepository.findByIncidentIdOrderByTimestampAsc(incidentId)
                .stream()
                .map(this::mapAuditToDTO)
                .toList();
    }

    // 4. Metrics
    public MetricsResponseDTO getMetrics() {

        return MetricsResponseDTO.builder()
                .totalIncidents(incidentReportRepository.countTotal())
                .openIncidents(incidentReportRepository.countOpen())
                .resolvedIncidents(incidentReportRepository.countResolved())
                .avgResolutionTime(incidentReportRepository.avgResolutionTime())
                .avgAssignmentTime(incidentReportRepository.avgAssignmentTime())
                .assignmentSLA_BreachedCount(incidentReportRepository.countAssignmentSLA_Breached())
                .resolutionSLA_BreachedCount(incidentReportRepository.countResolutionSLA_Breached())

                .build();
    }
public List<TopFailingServicesDTO> getTopFailingServices(){
        List<Object[]> services=incidentReportRepository.getServicesWithThereFailingCount();
        return services.stream().map(arry-> TopFailingServicesDTO.builder().serviceName((String)arry[0])
                        .count((long)arry[1])
                        .build()).toList();
}

public List<IncidentsHappeningTrendDTO>getIncidentsHappenedTrends(int days){
    LocalDateTime startDate=LocalDateTime.now().minusDays(days);
    List <Object[]>trend= incidentReportRepository.findIncidentTrends(startDate);

    return trend.stream().map(arry->IncidentsHappeningTrendDTO.builder()
            .incidentsDate((Date) arry[0])
            .count((long) arry[1])
            .build()).toList();
}


    // ---------------- MAPPERS ----------------

    private IncidentReportResponseDTO mapToDTO(IncidentReport i) {
        return IncidentReportResponseDTO.builder()
                .incidentId(i.getIncidentId())
                .title(i.getTitle())
                .severity(i.getSeverity())
                .priority(i.getPriority())
                .status(i.getStatus())
                .serviceName(i.getServiceName())
                .category(i.getCategory())
                .createdBy(i.getCreatedBy())
                .assignedTo(i.getAssignedTo())
                .assignedBy(i.getAssignedBy())
                .createdAt(i.getCreatedAt())
                .assignedAt(i.getAssignedAt())
                .resolvedAt(i.getResolvedAt())
                .timeToAssignSeconds(i.getTimeToAssignSeconds())
                .resolutionTimeSeconds(i.getResolutionTimeSeconds())
                .build();
    }

    private IncidentAuditDTO mapAuditToDTO(IncidentAuditLog log) {
        return IncidentAuditDTO.builder()
                .incidentId(log.getIncidentId())
                .eventId(log.getEventId())
                .eventType(log.getEventType().name())
                .performedBy(log.getPerformedBy())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .timestamp(log.getTimestamp())
                .build();
    }
}
