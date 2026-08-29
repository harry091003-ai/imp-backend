package com.example.UserService.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MessageDTO {
    private String SuccessMessage;
    @Builder.Default
    private String ErrorMessage="No Error";
}
