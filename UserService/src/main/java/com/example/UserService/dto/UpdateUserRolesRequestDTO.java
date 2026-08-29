package com.example.UserService.dto;


import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRolesRequestDTO {
    private Set<String> role;
}
