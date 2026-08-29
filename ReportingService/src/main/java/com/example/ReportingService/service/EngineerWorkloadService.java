package com.example.ReportingService.service;

import com.example.ReportingService.dto.EngineerWorkloadDTO;
import com.example.ReportingService.entity.EngineerWorkload;
import com.example.ReportingService.entity.IncidentReport;
import com.example.ReportingService.repository.EngineerWorkloadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

// This Service mainly Helps in Updating the the Total active Incident Count For Different Cases
// like when the Incident is resolved (decrease value by 1)
// or when  the Incident is assigned )Increase value by 1)
// Future Case(which is pending for now):
//  when the incident is Reassigned (Decrease value by 1 for old engineer and Increase value by 1 for new engineer)
@Service
@RequiredArgsConstructor
public class EngineerWorkloadService {
private  final EngineerWorkloadRepository engineerWorkloadRepository;
    public void IncrementActiveIncidentCount(IncidentReport incidentReport, UUID engineerId){
          EngineerWorkload engineerWorkload= engineerWorkloadRepository.findByEngineerId(engineerId).orElse(EngineerWorkload.builder()
                          .engineerId(engineerId)
                          .activeIncidentCount(0)
                          .updatedAt(LocalDateTime.now())
                  .build());

          engineerWorkload.setActiveIncidentCount(engineerWorkload.getActiveIncidentCount()+1);
          engineerWorkload.setLastAssignedAt(incidentReport.getAssignedAt());
          engineerWorkload.setUpdatedAt(LocalDateTime.now());
            engineerWorkloadRepository.save(engineerWorkload);
    }


    public void DecrementActiveIncidentCount(IncidentReport incidentReport, UUID engineerId){
        EngineerWorkload engineerWorkload= engineerWorkloadRepository.findByEngineerId(engineerId).orElse(null);
        if(engineerWorkload!=null && engineerWorkload.getActiveIncidentCount()>0)
        {
            engineerWorkload.setActiveIncidentCount(engineerWorkload.getActiveIncidentCount()-1);
            engineerWorkload.setUpdatedAt(LocalDateTime.now());
            engineerWorkloadRepository.save(engineerWorkload);
        }
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
