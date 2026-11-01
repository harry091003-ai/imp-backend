package com.example.ReportingService.controller;

import com.example.ReportingService.dto.EngineerPerformanceStatsDTO;
import com.example.ReportingService.dto.EngineerWorkloadDTO;
import com.example.ReportingService.service.EngineerPerformanceService;
import com.example.ReportingService.service.EngineerWorkloadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI plan phase 1 (2026-11-01): one call for all of Ai-Service's candidates, instead of 2 calls per engineer.
 * Only the URL is tested here: the controller with fake services.
 */
class EngineerSummariesEndpointTest {

    private final EngineerPerformanceService performance = mock(EngineerPerformanceService.class);
    private final EngineerWorkloadService workload = mock(EngineerWorkloadService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new InternalReportingContoller(performance, workload)).build();

    private static final String URL = "/internal/reports/engineer-summaries";

    @Test
    void eachEngineerGetsTheirStatsAndWorkloadTogether() throws Exception {
        UUID engineer = UUID.randomUUID();
        when(performance.engineerPerformanceStats(engineer, "DATABASE", "PAYMENT_SERVICE")).thenReturn(
                EngineerPerformanceStatsDTO.builder().engineerId(engineer).totalIncidentsResolved(4)
                        .expertiseScore(70).slaSuccessRate(0.9).build());
        when(workload.engineerWorkloadStats(engineer)).thenReturn(
                EngineerWorkloadDTO.builder().engineerId(engineer).activeIncidentCount(2).build());

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"category\":\"DATABASE\",\"serviceName\":\"PAYMENT_SERVICE\",\"engineerIds\":[\"" + engineer + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].engineerId").value(engineer.toString()))
                .andExpect(jsonPath("$[0].totalIncidentsResolved").value(4))
                .andExpect(jsonPath("$[0].expertiseScore").value(70.0))
                .andExpect(jsonPath("$[0].slaSuccessRate").value(0.9))
                .andExpect(jsonPath("$[0].activeIncidentCount").value(2));
    }

    @Test
    void noEngineersIsRefused() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"engineerIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moreThanFiftyEngineersIsRefused() throws Exception {
        String ids = String.join("\",\"", Collections.nCopies(51, UUID.randomUUID().toString()));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"engineerIds\":[\"" + ids + "\"]}"))
                .andExpect(status().isBadRequest());
    }
}
