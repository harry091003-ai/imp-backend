package com.example.UserService.dto;

import com.example.UserService.entity.Status;
import lombok.*;

import java.util.UUID;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StatusResponseDTO {
    private UUID userid;
    private String username;
    private String email;
    private Status status;
    private String message;;
}
