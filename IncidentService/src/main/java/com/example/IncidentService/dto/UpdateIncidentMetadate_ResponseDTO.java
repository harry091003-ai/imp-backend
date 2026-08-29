package com.example.IncidentService.dto;

import com.example.IncidentService.entity.Environment;
import com.example.IncidentService.entity.Priority;
import com.example.IncidentService.entity.Severity;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
@Builder
public class UpdateIncidentMetadate_ResponseDTO {
    private UUID id;
    private String updatedTitle;
    private String updatedDescription;
    private Severity updatedSeverity;
    private Priority updatedPriority;
    private String updatedAffectedSource;
    private Environment updatedEnvironment;
    private String updatedLocation;
    private LocalDateTime updatedAt;

}
