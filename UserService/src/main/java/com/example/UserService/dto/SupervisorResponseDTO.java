package com.example.UserService.dto;

import com.example.UserService.entity.Status;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SupervisorResponseDTO {
    private UUID userid;

    private String username;

    private String email;

}
