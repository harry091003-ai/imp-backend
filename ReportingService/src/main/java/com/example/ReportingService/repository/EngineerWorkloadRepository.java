package com.example.ReportingService.repository;

import com.example.ReportingService.entity.EngineerWorkload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EngineerWorkloadRepository extends JpaRepository<EngineerWorkload, UUID> {
    public Optional<EngineerWorkload> findByEngineerId(UUID engineerId);
}
