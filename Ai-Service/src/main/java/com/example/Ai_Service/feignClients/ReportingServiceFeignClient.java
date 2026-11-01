package com.example.Ai_Service.feignClients;

import com.example.Ai_Service.dto.EngineerSummaryDTO;
import com.example.Ai_Service.dto.EngineerSummaryRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "ReportingService")
public interface ReportingServiceFeignClient {

    // AI plan phase 1: one call for all candidates (it was 2 calls per engineer, one after the other).
    @PostMapping("/internal/reports/engineer-summaries")
    List<EngineerSummaryDTO> getEngineerSummaries(@RequestBody EngineerSummaryRequestDTO request);
}
