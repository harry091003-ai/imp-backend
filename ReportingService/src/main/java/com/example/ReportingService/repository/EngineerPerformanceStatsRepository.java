package com.example.ReportingService.repository;

import com.example.ReportingService.entity.EngineerPerformanceStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface EngineerPerformanceStatsRepository extends JpaRepository<EngineerPerformanceStats, UUID> {

public Optional<EngineerPerformanceStats>findByEngineerIdAndCategoryAndServiceName(UUID engineerId, String category, String serviceName);


}
