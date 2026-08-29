package com.example.ReportingService.repository;

import com.example.ReportingService.entity.IncidentReport;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, UUID> {
    Page<IncidentReport> findByStatus(String status, Pageable Storedpageable);

    Page<IncidentReport> findByStatusAndSeverity(String status, String severity, Pageable Storedpageable);


    @Query("SELECT COUNT(i) FROM IncidentReport i")
    long countTotal();

    @Query("SELECT COUNT(i) FROM IncidentReport i WHERE i.status = 'OPEN'")
    long countOpen();

    @Query("SELECT COUNT(i) FROM IncidentReport i WHERE i.status = 'RESOLVED'")
    long countResolved();

    @Query("SELECT AVG(i.resolutionTimeSeconds) FROM IncidentReport i WHERE i.resolutionTimeSeconds IS NOT NULL")
    Double avgResolutionTime();

    @Query("SELECT AVG(i.timeToAssignSeconds) FROM IncidentReport i WHERE i.timeToAssignSeconds IS NOT NULL")
    Double avgAssignmentTime();

    @Query("SELECT COUNT(i) FROM IncidentReport i WHERE i.assignmentSlaBreached IS TRUE")
    long countAssignmentSLA_Breached();

    @Query("SELECT COUNT(i) FROM IncidentReport i WHERE i.resolutionSlaBreached IS TRUE")
    long countResolutionSLA_Breached();

    @Query("SELECT i.serviceName , COUNT(i) FROM IncidentReport i GROUP BY i.serviceName ORDER BY COUNT(i) DESC")
    List<Object[]> getServicesWithThereFailingCount();

    //find number of incidents  happens from the startDate.
    @Query("SELECT DATE(i.createdAt) ,COUNT(i) FROM IncidentReport i WHERE i.createdAt >= :startDate GROUP BY DATE(i.createdAt) ORDER BY DATE(i.createdAt)")
    List<Object[]> findIncidentTrends(@Param("startDate") LocalDateTime startDate);

    // for fetching the IncidentReports where breach is set to true but not published yet
    //along with query , hamm yaha pr orderBy lga dete hai jese jo rows ye condition satisfy karte hai unme(createdAt and incidentId) ki combination ki accending order ki basis pr  un rows ko pehle send kar denge..jisse performation and consistency bane rahege
// 1.Suppose we have   1000 rows which satisfy condition  :
// 2. then as we apply order by so ye sort karge us order mai
// 3. then Limit applied ha because of Pageable so we get first 100  sorted rows
    @Query("""
            SELECT i FROM IncidentReport i
            WHERE i.assignmentSlaBreached = true
            AND i.assignmentSlaEventPublished = false
            ORDER BY i.createdAt, i.incidentId
            """)
    List<IncidentReport> findAssignmentEventsToPublish(Pageable pageRequest);

    //same approach as above comments
    @Query("""
            SELECT i FROM IncidentReport i
            WHERE i.resolutionSlaBreached = true
            AND i.resolutionSlaEventPublished = false
            ORDER BY i.createdAt, i.incidentId
            """)
    List<IncidentReport> findResolutionEventsToPublish(Pageable pageRequest);


    // 🔥 Bulk update: breach to true(IMPORTANT for performance & race condition)
    @Modifying
    @Transactional
    @Query("""
            UPDATE IncidentReport i
            SET i.assignmentSlaBreached = true
            WHERE i.assignmentDeadline < :now
            AND i.assignmentSlaBreached = false
            AND i.assignedAt IS NULL
            """)
    int markAssignmentSlaBreached(@Param("now") LocalDateTime now);

    //same approach as above
    @Modifying
    @Transactional
    @Query("UPDATE IncidentReport i " +
            "SET i.resolutionSlaBreached = true" +
            " WHERE i.resolutionDeadline < :now " +
            "AND i.resolutionSlaBreached = false " +
            "AND i.resolvedAt IS NULL AND i.status != 'RESOLVED'")
    int markResolutionSlaBreached(@Param("now") LocalDateTime now);


    //Query for getting the incident where Assignment SLABreach occurs after  30 min which is ~ 1800 seconds
//    here we specially apply orderby because we ki assignmentDeadline ki basis pr sorting rahe jisse
//    1.jo row sabse pehle breach hone wali hai wo sabse pehle milegi
    // 2. order by (sorting) lgane se vo issue bhi resolve ho geya :  suppose limit offest nai first page ki rows read karle  and then next page pr chala geya but ab  koi row  page 1 ki assignbreach risk mai aa gaye to vo es schedulling cycle mai nahi ayege

    @Query("""
            SELECT ir
            FROM IncidentReport ir
            WHERE ir.assignedAt IS NULL
            AND ir.assignmentSlaBreached = false
            AND ir.assignmentDeadline IS NOT NULL
            AND ir.assignmentDeadline <= :AssignmentDeadlineWindow
            AND (ir.assignmentDeadline >:BatchLastEntry_AssignedDeadline OR (ir.assignmentDeadline = :BatchLastEntry_AssignedDeadline AND ir.incidentId > :BatchLastEntry_IncidentId))
            ORDER BY ir.assignmentDeadline ASC , ir.incidentId ASC
            
            """)
    List<IncidentReport> findAssignmentSLARiskIncidents(
            @Param("AssignmentDeadlineWindow") LocalDateTime AssignmentDeadlineWindow,
            @Param("BatchLastEntry_AssignedDeadline")  LocalDateTime BatchLastEntry_AssignedDeadline,
            @Param("BatchLastEntry_IncidentId") UUID BatchLastEntry_IncidentId, Pageable pageable
    );

   //     same approach as above
    @Transactional
    @Query("""
            SELECT ir
            FROM IncidentReport ir
            WHERE ir.resolvedAt IS NULL
            AND ir.resolutionSlaBreached = false
            AND ir.resolutionDeadline IS NOT NULL
            AND ir.resolutionDeadline <= :ResolutionDeadlineWindow
            AND (ir.resolutionDeadline >:BatchLastEntry_ResolutionDeadline OR (ir.resolutionDeadline = :BatchLastEntry_ResolutionDeadline AND ir.incidentId > :BatchLastEntry_IncidentId))
            ORDER BY ir.resolutionDeadline ASC , ir.incidentId ASC
            """)
    List<IncidentReport> findResolutionSLARiskIncidents(
            @Param("ResolutionDeadlineWindow") LocalDateTime ResolutionDeadlineWindow,
            @Param("BatchLastEntry_ResolutionDeadline")  LocalDateTime BatchLastEntry_ResolutionDeadline,
            @Param("BatchLastEntry_IncidentId") UUID BatchLastEntry_IncidentId,
            Pageable pageable
            );


    @Query("""
       SELECT COUNT(ir)
       FROM IncidentReport ir
       WHERE ir.assignedTo = :engineerId
       AND ir.status NOT IN ('RESOLVED', 'CLOSED')
       """)
    int countActiveIncidents(
            @Param("engineerId") UUID engineerId
    );


























}

