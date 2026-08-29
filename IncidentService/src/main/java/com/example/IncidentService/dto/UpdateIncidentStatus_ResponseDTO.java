package com.example.IncidentService.dto;

import com.example.IncidentService.entity.IncidentStatus;
import jdk.jshell.Snippet;
import lombok.*;

import java.util.UUID;
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
@Builder
public class UpdateIncidentStatus_ResponseDTO {

    private UUID id;
    private IncidentStatus updatedStatus;
    private String message;
}
