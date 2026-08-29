package com.example.ReportingService.dto;

import lombok.*;

import java.util.Date;
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class IncidentsHappeningTrendDTO {
    private Date incidentsDate;
    private long count;
}
