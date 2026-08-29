package com.example.ReportingService.repository;

import com.example.ReportingService.entity.DailyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DailtyMetricsReportRepository extends JpaRepository<DailyMetrics, UUID> {
}
