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

    // Added B4 step 2. These two had no limit, even though the comment above
    // already claimed the limits mirror the entity columns. Both columns are
    // varchar(255), same as title.
    @Size(max = 255, message = "Affected source must be at most 255 characters")
    private String affectedSource;
    private Environment environment;
    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;

}
