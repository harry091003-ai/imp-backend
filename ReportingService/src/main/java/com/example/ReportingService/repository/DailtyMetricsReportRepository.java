package com.example.ReportingService.repository;

import com.example.ReportingService.entity.DailyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

// Issue 61: the id type said UUID, but the primary key of this table is the date.
public interface DailtyMetricsReportRepository extends JpaRepository<DailyMetrics, LocalDate> {

    List<DailyMetrics> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);
}
