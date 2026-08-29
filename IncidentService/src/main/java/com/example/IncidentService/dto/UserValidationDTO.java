package com.example.IncidentService.dto;

import lombok.*;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserValidationDTO {

    private UUID userid;
    private String status;
    private Set<String> roles;

}
