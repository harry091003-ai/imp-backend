package com.example.IncidentService.dto;

import com.example.IncidentService.entity.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
@Builder
public class UpdateIncidentStatus_RequestDTO {
    // was @Column(nullable=false) - a JPA annotation, which is inert on a DTO and was
    // never enforcing anything. @NotNull is the one that actually runs under @Valid.
    @NotNull(message = "Status is required")
    private IncidentStatus status;

    @Size(max = 2000, message = "Root cause must be at most 2000 characters")
    private String rootCause;

    @Size(max = 2000, message = "Resolution notes must be at most 2000 characters")
    private String resolutionNotes;
}
