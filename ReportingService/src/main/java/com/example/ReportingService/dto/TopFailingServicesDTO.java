package com.example.ReportingService.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TopFailingServicesDTO {
    private String serviceName;
    private long count;
}
