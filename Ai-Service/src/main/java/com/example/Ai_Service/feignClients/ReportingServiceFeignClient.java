package com.example.Ai_Service.feignClients;

import com.example.Ai_Service.dto.EngineerPerformanceStatsDTO;
import com.example.Ai_Service.dto.EngineerWorkloadDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "ReportingService")
public interface ReportingServiceFeignClient {

    @GetMapping(
            "/internal/reports/engineer-performance/{engineerId}/{category}/{service}"
    )
    EngineerPerformanceStatsDTO getEngineerPerformanceStats(
            @PathVariable UUID engineerId,
            @PathVariable String category,
            @PathVariable String service
    );

    @GetMapping(
            "/internal/reports/engineer-workload/{engineerId}"
    )
    EngineerWorkloadDTO getEngineerWorkload(
            @PathVariable UUID engineerId
    );
}
