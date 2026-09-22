package com.example.IncidentService.dto;

import com.example.IncidentService.entity.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateIncidentRequestDTO {
//@NotNull means value cannot be null but can be empty or or just widespace
    //@NotBlank means value cannot be null and cannot be empty and not just whitespace
    @NotBlank(message = "Title is required")
    @Size(max = 255)
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 2000)
    private String description;

    // Added B4 step 2. Without these, an incident could be created with no
    // severity/priority/etc at all. ReportingService.SLAConfiguration then does
    // switch(priority) on a null value - an NPE, which sends the event to the
    // dead letter topic and that incident never gets a report or an SLA.
    @NotNull(message = "Severity is required")
    private Severity severity;

    @NotNull(message = "Priority is required")
    private Priority priority;

    // affectedSource/location: same column size as title (255), but had no
    // limit here before. Added so a value MySQL will reject is caught here
    // first, as a clean 400, instead of reaching the database.
    @Size(max = 255, message = "Affected source must be at most 255 characters")
    private String affectedSource;

    @NotNull(message = "Environment is required")
    private Environment environment;

    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;

    @NotNull(message = "Service name is required")
    private ServiceName serviceName;

    @NotNull(message = "Category is required")
    private IncidentCategory category;
}
