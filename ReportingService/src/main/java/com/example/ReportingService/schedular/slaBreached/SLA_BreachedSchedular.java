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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
//Outbox step 4 (2026-10-13): the Outbox Pattern is now applied here (this line used to say "in future").
//Before: send to Kafka, THEN mark "published" in a second step. A crash in between = the event sent twice, or a
//failed send = marked published but never sent (known issue 22). Now each page is ONE transaction: the event rows
//go into the outbox table AND the flags are set, together. The background relay sends the rows a moment later.
//we use bulk update because it is atomic, prevents race conditions, avoids duplicate processing, and scales efficiently in distributed systems, whereas setter + save is non-atomic and unsafe in concurrent environments.

@RequiredArgsConstructor
@Component
public class SLA_BreachedSchedular {
private final IncidentReportRepository incidentReportRepository;
        private static final Logger log = LoggerFactory.getLogger(SLA_BreachedSchedular.class);
        private final SLA_BreachedEventProducer slaBreachedEventProducer;
        // Outbox step 4: one short transaction per page (events saved + flags set, or neither).
        private final TransactionTemplate transactionTemplate;

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
        // Always page 0: the rows we just marked no longer match the query, so the next page 0 is the next 100.
        PageRequest pagerequest= PageRequest.of(0,100);
        int assignmentPageSize;
do {
    // 🔹 Step 3: ONE transaction per page: save each event in the outbox AND mark the report as published.
    // If anything fails, the whole page rolls back (no flag, no event) and the next run tries it again.
    assignmentPageSize = transactionTemplate.execute(status -> {
        List<IncidentReport> assignmentBreachesIncidentReports = incidentReportRepository.findAssignmentEventsToPublish(pagerequest);
        for (IncidentReport incident : assignmentBreachesIncidentReports) {
            //save the event in the outbox (the relay sends it to Kafka a moment later)
            slaBreachedEventProducer.publishAssignmentBreachedEvent(incident);
            //  mark that incidentreport as published
            incident.setAssignmentSlaEventPublished(true);
        }
        //save in bulk
        incidentReportRepository.saveAll(assignmentBreachesIncidentReports);
        return assignmentBreachesIncidentReports.size();
    });
}
while (assignmentPageSize > 0);


        //Fetch candidates in Batches of 100 using Pagenation (where Resolution breach is true but did not publish event yet)
        int resolutionPageSize;
do {
    // 🔹 Step 3: same as above - one transaction per page.
    resolutionPageSize = transactionTemplate.execute(status -> {
        List<IncidentReport> resolvementBreachesIncidentReports = incidentReportRepository.findResolutionEventsToPublish(pagerequest);
        for (IncidentReport incident : resolvementBreachesIncidentReports) {
            //save the event in the outbox
            slaBreachedEventProducer.publishResolutionBreachedEvent(incident);
            //  mark that incidentreport as published
            incident.setResolutionSlaEventPublished(true);
        }
        //save in bulk
        incidentReportRepository.saveAll(resolvementBreachesIncidentReports);
        return resolvementBreachesIncidentReports.size();
    });
}
while (resolutionPageSize > 0);

    }

}
//