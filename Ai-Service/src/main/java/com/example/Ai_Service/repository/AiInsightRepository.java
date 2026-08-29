package com.example.Ai_Service.repository;

import com.example.Ai_Service.entity.AiInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiInsightRepository extends JpaRepository<AiInsight, UUID> {
}
