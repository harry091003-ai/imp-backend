package com.example.IncidentService.repository;

import com.example.IncidentService.entity.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    @Query("""
             SELECT i FROM Incident i\s
                 WHERE (
                     :isAdmin = true
                     OR\s
                     (:isSupervisor = true AND (
                         i.assignedTo IN :teamEngineerIds\s
                         OR i.createdBy = :userId
                     ))
                     OR\s
                     (:isEngineer = true AND (
                         i.assignedTo = :userId\s
                         OR i.createdBy = :userId
                     ))
                 )
            """)
    Page<Incident>findIncidentsForUser(UUID userId,
                                       boolean isAdmin,
                                       boolean isSupervisor,
                                       boolean isEngineer,
                                       List<UUID> teamEngineerIds,
                                       Pageable pageable);




    Page<Incident> findByCreatedBy(UUID userId, Pageable pageable);

     Page<Incident> findByAssignedTo(UUID userId, Pageable pageable);

     Page<Incident> findByAssignedBy(UUID userId, Pageable pageable);
}
