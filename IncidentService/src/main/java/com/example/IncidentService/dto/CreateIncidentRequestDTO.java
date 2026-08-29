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


    private Severity severity;

    private Priority priority;


    private String affectedSource;


    private Environment environment;


    private String location;

    private ServiceName serviceName;

    private IncidentCategory category;
}
