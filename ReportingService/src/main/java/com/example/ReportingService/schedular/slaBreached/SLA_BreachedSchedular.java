package com.example.ReportingService.schedular.slaBreached;

import com.example.ReportingService.entity.IncidentReport;
import com.example.ReportingService.kafka.producer.SLA_BreachedEventProducer;
import com.example.ReportingService.repository.IncidentReportRepository;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
//in future we need to apply Outbox Pattern instead of the following approach as there many bugs...
//we use bulk update because it is atomic, prevents race conditions, avoids duplicate processing, and scales efficiently in distributed systems, whereas setter + save is non-atomic and unsafe in concurrent environments.

@RequiredArgsConstructor
@Component
public class SLA_BreachedSchedular {
private final IncidentReportRepository incidentReportRepository;
        private static final Logger log = LoggerFactory.getLogger(SLA_BreachedSchedular.class);
        private final SLA_BreachedEventProducer slaBreachedEventProducer;

    @Scheduled(fixedDelay = 70000)
    @SchedulerLock(name = "slaScheduler",lockAtLeastFor = "PT50S",lockAtMostFor = "PT2M")
    public void checkSlaBreaches(){
        log.info("SLA Scheduler started at {}", LocalDateTime.now());
        // 🔹 Step 1: Bulk update (atomic approach) : we are using update query for updating the SLA Value from false to true....
        // instead(non atomic approach) of using for loop where we set the value to true using setter and then finally  save the assignmentBreached in IncidentReports using save method. for each entry
        //because 1. if multiple instances  are present of this service
        // A.then scheduler in both the service do this same task so duplication..which is not in update case
        //B. and if both the services do different tasks like delete or modify...there is high chance of raise conditions because it happens in multiple steps Fetch records->modify values->save to db....
        //unlike our bulk update where save in a single step.
       int assignmentUpdated =incidentReportRepository.markAssignmentSlaBreached(LocalDateTime.now());
       int resolutionUpdated= incidentReportRepository.markResolutionSlaBreached(LocalDateTime.now());

        log.info("Assignment SLA breached count: {}", assignmentUpdated);
        log.info("Resolution SLA breached count: {}", resolutionUpdated);

        //Fetch candidates in Batches of 100 using Pagenation (where Assignment breach is true but did not publish event yet)
        PageRequest pagerequest= PageRequest.of(0,100);
        List<IncidentReport> assignmentBreachesIncidentReports;
do {
     assignmentBreachesIncidentReports = incidentReportRepository.findAssignmentEventsToPublish(pagerequest);
// 🔹 Step 3: Publish the events and then mark as published
    for (IncidentReport incident : assignmentBreachesIncidentReports) {
        //publish
        slaBreachedEventProducer.publishAssignmentBreachedEvent(incident);
        //  mark that incidentreport as published
        incident.setAssignmentSlaEventPublished(true);
    }
    ;
    //save in bulk
    incidentReportRepository.saveAll(assignmentBreachesIncidentReports);
}
while (!assignmentBreachesIncidentReports.isEmpty());


        //Fetch candidates in Batches of 100 using Pagenation (where Resolution breach is true but did not publish event yet)
        List<IncidentReport> resolvementBreachesIncidentReports;
do {
     resolvementBreachesIncidentReports = incidentReportRepository.findResolutionEventsToPublish(pagerequest);
    // 🔹 Step 3: Publish the events and then mark as published
     for (IncidentReport incident : resolvementBreachesIncidentReports) {
        //publish
        slaBreachedEventProducer.publishResolutionBreachedEvent(incident);
        //  mark that incidentreport as published
        incident.setResolutionSlaEventPublished(true);
    }
    //save in bulk
    incidentReportRepository.saveAll(resolvementBreachesIncidentReports);
}
while (!resolvementBreachesIncidentReports.isEmpty());

    }

}
//