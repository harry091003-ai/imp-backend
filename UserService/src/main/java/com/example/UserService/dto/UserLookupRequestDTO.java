package com.example.UserService.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

// BCR-05: the ids one screen needs names for. At most 100, so one call can never read the whole user table.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserLookupRequestDTO {
    @NotNull(message = "ids is required")
    @Size(max = 100, message = "At most 100 user ids per request")
    private List<@NotNull(message = "An id must not be empty") UUID> ids;
}
