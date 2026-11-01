package com.example.ReportingService.service;

import com.example.ReportingService.dto.EngineerWorkloadDTO;
import com.example.ReportingService.entity.EngineerWorkload;
import com.example.ReportingService.entity.IncidentReport;
import com.example.ReportingService.repository.EngineerWorkloadRepository;
import com.example.ReportingService.repository.IncidentReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

// This Service keeps the Total active Incident Count of each engineer.
// Issue 62 (2026-11-01): it used to add and subtract 1 by hand, and the number drifted: a reassign never took 1 from the
// old engineer, a resolve took 1 from whoever pressed the button, a reopen never added 1 back.
// Now it COUNTS the real rows (assigned to this engineer, not RESOLVED or CLOSED). So every case is covered by one rule,
// and a number that is already wrong corrects itself at that engineer's next event.
@Service
@RequiredArgsConstructor
public class EngineerWorkloadService {
private  final EngineerWorkloadRepository engineerWorkloadRepository;
private final IncidentReportRepository incidentReportRepository;

    // Call it AFTER saving the report: the count reads the report table (Hibernate writes the pending change first).
    public void refreshActiveIncidentCount(UUID engineerId){
        EngineerWorkload engineerWorkload= engineerWorkloadRepository.findByEngineerId(engineerId).orElse(EngineerWorkload.builder()
                        .engineerId(engineerId)
                        .build());

        engineerWorkload.setActiveIncidentCount(incidentReportRepository.countActiveIncidents(engineerId));
        engineerWorkload.setUpdatedAt(LocalDateTime.now());
        engineerWorkloadRepository.save(engineerWorkload);
    }

    // The new engineer of an assign: the count, plus the time of this assign.
    public void refreshAfterAssignment(IncidentReport incidentReport, UUID engineerId){
        refreshActiveIncidentCount(engineerId);
        EngineerWorkload engineerWorkload= engineerWorkloadRepository.findByEngineerId(engineerId).orElseThrow();
        engineerWorkload.setLastAssignedAt(incidentReport.getAssignedAt());
        engineerWorkloadRepository.save(engineerWorkload);
    }


    public EngineerWorkloadDTO engineerWorkloadStats(UUID engineerId){
       EngineerWorkload engineerWorkload= engineerWorkloadRepository.findByEngineerId(engineerId).orElse(null);
        if (engineerWorkload == null) {

            return EngineerWorkloadDTO.builder()
                    .engineerId(engineerId)
                    .activeIncidentCount(0)
                    .build();
        }
        return EngineerWorkloadDTO.builder()
                .engineerId(engineerWorkload.getEngineerId())
                .activeIncidentCount(engineerWorkload.getActiveIncidentCount())
                .lastAssignedAt(engineerWorkload.getLastAssignedAt())
                .updatedAt(engineerWorkload.getUpdatedAt())
                .build();


    }

}
