package com.example.Ai_Service.repository;

import com.example.Ai_Service.entity.ClassificationSuggestion;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ClassificationSuggestionRepository extends JpaRepository<ClassificationSuggestion, String> {

    @Transactional
    @Modifying
    @Query("DELETE FROM ClassificationSuggestion s WHERE s.createdAt < :limit")
    void deleteOlderThan(@Param("limit") LocalDateTime limit);
}
