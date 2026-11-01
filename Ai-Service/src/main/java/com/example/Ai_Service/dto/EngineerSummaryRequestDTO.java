package com.example.Ai_Service.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineerSummaryRequestDTO {

    private String category;
    private String serviceName;
    private List<UUID> engineerIds;
}
