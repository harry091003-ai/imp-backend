package com.example.ReportingService.service;

import com.example.ReportingService.entity.ProcessedEvent;
import com.example.ReportingService.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
//Important Concept**
// In isDuplicate function if eventId already present then we are here throwing the Exception .. that thrown exception  go to our try block of our consumeIncidentAssignedEvent method...
//as the exception happened so it is handled by our catch block in consumeIncidentAssignedEvent...

// since if exception happend then due to @transactional this whole get rolls back
//and as in consumeIncidentAssignedEvent's  functioin , catch block handle the transaction  so not retry occurs by kafka listener ...just transaction rollback

// we are handling exception in the consumeIncidentAssignedEvent's catch block instead of directly handling from this isDuplicate catch block because vaha ki @transactional and and Yaha ka @transactional different ho jata then transactional boundry cross ho jate so not able to roll back the consumeIncidentAssignedEvent function.
//but   if  consumeIncidentAssignedEvent function throw exception in catch block then kafka retry will occurs because then system understand that the exception in not handled by catch block thats why it through exception so then rollback occurs and also then retry occurs.
public class IdempotencyService {
    private  final ProcessedEventRepository processedEventRepository;

    public void isDuplicate(UUID eventId) {
        ProcessedEvent processedEvent=ProcessedEvent.builder()
                .eventId(eventId)
                .processedAt(LocalDateTime.now())//Here for ProcessedAt , Always store: when system processed event not when event happened
                .build();
        try {
            processedEventRepository.save(processedEvent);
        } catch (DataIntegrityViolationException ex) {
            throw new DataIntegrityViolationException("Entry with same eventId already exist in  processedEvent table") ;
        }
    }
}
