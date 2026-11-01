package com.example.ReportingService.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

// AI plan phase 1: Ai-Service asks about all its candidates in ONE call (it used to be 2 calls per engineer).
// category and serviceName are optional: without them nobody has history for "that kind of incident".
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EngineerSummaryRequestDTO {

    private String category;
    private String serviceName;

    @NotEmpty(message = "At least one engineer id is required")
    @Size(max = 50, message = "At most 50 engineer ids per request")
    private List<@NotNull UUID> engineerIds;
}
