package com.example.ReportingService.schedular.dailyMetrics;

import com.example.ReportingService.service.DailyMetricsService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// Issue 61 (2026-11-01): fills the daily_metrics table.
// Every hour, not once at midnight: a job fixed at midnight simply never runs on a machine that is off at midnight.
// Each run works out the last 7 full days again, so missed days are filled in by the next run.
@RequiredArgsConstructor
@Component
public class DailyMetricsSchedular {

    private static final Logger log = LoggerFactory.getLogger(DailyMetricsSchedular.class);

    private final DailyMetricsService dailyMetricsService;

    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    @SchedulerLock(name = "dailyMetricsScheduler", lockAtLeastFor = "PT5M", lockAtMostFor = "PT10M")
    public void refreshDailyMetrics() {
        dailyMetricsService.refreshRecentDays(LocalDate.now());
        log.info("Daily metrics refreshed");
    }
}
