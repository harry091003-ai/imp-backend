package com.example.IncidentService.dto;

import com.example.IncidentService.entity.Environment;
import com.example.IncidentService.entity.IncidentStatus;
import com.example.IncidentService.entity.Priority;
import com.example.IncidentService.entity.Severity;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
@Builder
public class UpdateIncidentMetadate_RequestDTO {
    // All fields are optional here on purpose: this is a partial update and the service
    // only applies non-blank values. The @Size limits mirror the Incident entity columns.
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;
    private Severity severity;
    private Priority priority;
    private String affectedSource;
    private Environment environment;
    private String location;

}
