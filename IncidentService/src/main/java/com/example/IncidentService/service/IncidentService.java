package com.example.IncidentService.service;

import com.example.IncidentService.client.UserClient;
import com.example.IncidentService.dto.*;
import com.example.IncidentService.entity.Incident;
import com.example.IncidentService.entity.IncidentStatus;
import com.example.IncidentService.kafka.producer.IncidentEventProducer;
import com.example.IncidentService.repository.IncidentRepository;
import com.project.events.IncidentAssignedEvent;
import com.project.events.IncidentCreatedEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import com.project.web.exception.ForbiddenException;
import com.project.web.exception.NotFoundException;
import com.project.web.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncidentService {

private final UserClient userClient;
private static final Logger log= LoggerFactory.getLogger(IncidentService.class);
private final IncidentRepository incidentRepository;
private final IncidentEventProducer incidentEventProducer;

   public CreateIncidentResponseDTO  CreateIncident(UUID userId, CreateIncidentRequestDTO request){
   //firstly we are checking is the User who try to create the incident is Active or not
       UserValidationDTO engineer = userClient.getUser(userId);
       boolean IsActive = engineer.getStatus().equals("ACTIVE");
       if (!IsActive) {
           log.warn("User with id {} is not authorized to create an incident.", userId);
           // 403: we know who they are, their account is just not in a state that may act.
           throw new ForbiddenException("USER_NOT_ACTIVE",
                   "Your account is not ACTIVE, so you cannot create an incident.");
       }
         // Proceed with incident creation logic here
       Incident incident=Incident.builder()
                       .title(request.getTitle())
                        .description(request.getDescription())
                        .severity(request.getSeverity())
                        .priority(request.getPriority())
                      .affectedSource(request.getAffectedSource())
                     .serviceName(request.getServiceName())
                     .category(request.getCategory())
                      .environment(request.getEnvironment())
                        .location(request.getLocation())
               .createdAt(LocalDateTime.now())
               .updatedAt(LocalDateTime.now())
               .createdBy(userId)
               .build();
         // Save the incident to the database
       Incident ourIncident= incidentRepository.save(incident);

       // as Incident is created in Db
       //So Trigger the Kafka Event using The EventProducer

     //    1. create Event
    IncidentCreatedEvent event=  IncidentCreatedEvent.builder()
               .incidentId(ourIncident.getId())
               .eventId(UUID.randomUUID())// used to uniquely identify the event and also used in Idempotency in case of retry mechanism , also used for tracing the event in distributed system(multiple Services)
               .performedBy(userId)// this field is used to know who performed the action of creating the incident .
               .title(ourIncident.getTitle())
               .description(ourIncident.getDescription())
               .createdBy(ourIncident.getCreatedBy())
               .severity(ourIncident.getSeverity()!=null?ourIncident.getSeverity().name():null)
               .priority(ourIncident.getPriority()!=null?ourIncident.getPriority().name():null)
               .createdAt(ourIncident.getCreatedAt())
            .serviceName(ourIncident.getServiceName()!=null?ourIncident.getServiceName().name():null)
            .category(ourIncident.getCategory()!=null?ourIncident.getCategory().toString():null)
            .message("Incident is successfully created!!")
               .build();
        //2.publish Incident Created Event to Kafka Topic
       incidentEventProducer.PublishIncidentCreated(ourIncident.getId().toString(),event);


       return CreateIncidentResponseDTO.builder().incidentId(ourIncident.getId()).
               message("Incident created Successfully").build();
   }


   //helper function for checking Both :
   // 1.is user is allowed to assign Incident
   // 2. is User status Active
//    public boolean validateUserCanCreateIncident(UUID userId){
//
//        UserValidationDTO user=userClient.getUser(userId);
//
//        boolean isAllowed =  user.getRoles().stream().anyMatch(role->role.equals("ROLE_ENGINEER") ||
//                role.equals("ROLE_SUPERVISOR") ||
//                role.equals("ROLE_ADMIN"));
//     if(!isAllowed){
//    log.warn("User with id {} does not have the required role to create an incident.", userId);
//         throw new RuntimeException("User not allowed to create incident");}
//        boolean isActive=user.getStatus().equals("ACTIVE");
//        if(!isActive){
//            log.warn("User with id {} is not active so cannot create an incident.", userId);
//            throw new RuntimeException("User is not ACTIVE");}
//        return true;
//    }

    public boolean IsUserActive(UUID userId){
        UserValidationDTO user=userClient.getUser(userId);
        boolean isActive=user.getStatus().equals("ACTIVE");
       return isActive;
    }

public AssignIncidentResponseDTO AssignIncident(UUID IncidentId, UUID assignedToUserId, Authentication auth) {
//1.firstly we are checking is the User who try to Assign the incident is Active or not
    UUID AssignerId=UUID.fromString(auth.getName());

    if (!IsUserActive(AssignerId)){
        log.warn("User with id {} is not authorized to Assign an incident.", AssignerId);
        // 403: the CALLER is not allowed to act.
        throw new ForbiddenException("USER_NOT_ACTIVE",
                "Your account is not ACTIVE, so you cannot assign an incident.");
    }

    Incident incident = incidentRepository.findById(IncidentId).orElseThrow(
            () -> new NotFoundException("INCIDENT_NOT_FOUND", "Incident not found: " + IncidentId));

    //2.Is the Engineer to whom we want to assign exist and  2. is he is active and 3.has the role of Engineer or not
    UserValidationDTO engineer = userClient.getUser(assignedToUserId);
    if (!IsUserActive(assignedToUserId)) {
        log.warn("User with id {} is not active so an incident cannot be assigned to him/her .", assignedToUserId);
        // 400 not 403: the caller is allowed to assign, they just picked an unusable target.
        // The fix is for the client to choose a different engineer, which is a 4xx-request problem.
        throw new ValidationException("ENGINEER_NOT_ACTIVE",
                "The selected engineer is not ACTIVE and cannot be assigned incidents.");
    }
    //we can only assign to a user With enginer role so we are checking that the user have the role of engineer or not
    boolean isEngineer = engineer.getRoles().stream().anyMatch(role -> role.equals("ROLE_ENGINEER"));
    if (!isEngineer) {
        log.warn("User with id {} does not have the required role to be assigned to an incident.", assignedToUserId);
        // 400: again a bad choice of target in the request, not a permission problem.
        throw new ValidationException("TARGET_USER_NOT_ENGINEER",
                "Incidents can only be assigned to a user with the ENGINEER role.");
    }


    UUID SupervisorOrAdminId = UUID.fromString(auth.getName());
    // if all above condition is true then we can assign the incident to that engineer
    incident.setAssignedTo(assignedToUserId);
    incident.setAssignedBy(SupervisorOrAdminId);
    incident.setUpdatedAt(LocalDateTime.now());
    incident.setIncidentStatus(IncidentStatus.ASSIGNED);

    Incident updatedIncident = incidentRepository.save(incident);

    // as Incident is Assigned in Db
    //So Trigger the Kafka Event using The EventProducer
    //1.Create Event
        IncidentAssignedEvent  event=IncidentAssignedEvent
                .builder()
                .incidentId(updatedIncident.getId())
                .eventId(UUID.randomUUID())// used to uniquely identify the event and also used in Idempotency in case of retry mechanism , also used for tracing the event in distributed system(multiple Services)
                .performedBy(AssignerId)// this field is used to know who performed the action of creating the incident .
                .title(updatedIncident.getTitle())
                .createdBy(updatedIncident.getCreatedBy())
                .assignedTo(assignedToUserId)
                .assignedBy(updatedIncident.getAssignedBy())
                .assignedAt(updatedIncident.getUpdatedAt())
                .serviceName(updatedIncident.getServiceName().name())
                .category(updatedIncident.getCategory().toString())
                .message("Incident is successfully assigned to Engineer with id " + assignedToUserId)
                .build();

        //2.publish IncidentAssigned Event to Kafka Topic
    incidentEventProducer.PublishIncidentAssigned(IncidentId.toString(),event);


    return AssignIncidentResponseDTO.builder()
            .enginerId(updatedIncident.getAssignedTo())
            .assignedBy(updatedIncident.getAssignedBy())
            .incidentid(updatedIncident.getId())
            .message("Incident assigned successfully to Engineer with id " + assignedToUserId.toString()).build();

}




// Getting Incidents Api: This API will return the incidents based on the role of the user:
//    Like If Admin: he can see all incidents
//    If Supervisor: he can see all incidents that are created by him or assigned to his team engineers
//    If Engineer: he can see all incidents that are assigned to him or created by him

    public Page<IncidentResponseDTO> getIncidentsBasedOnRoles(
            UUID userId,
            Authentication auth,
            Pageable pageable,
            String filter
    ) {

        // 1. Extract roles
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isSupervisor = roles.contains("ROLE_SUPERVISOR");
        boolean isEngineer = roles.contains("ROLE_ENGINEER");

        // 2. Get team engineers if supervisor
        List<UUID> teamEngineerIds = new ArrayList<>();

        if (isSupervisor) {
            teamEngineerIds = userClient.getEngineersUnderSupervisor(userId);
            if(teamEngineerIds.isEmpty()) {
                log.info("Supervisor with id {} has no engineers in his team.", userId);
            return Page.empty(pageable);
            }
        }
//3. Create a new Pageable with sorting by createdAt in descending order
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("createdAt").descending()
        );

//4 🔥 FILTER LOGIC (CLEAN SEPARATION) that will work if the user send the filter query param. to get specific result.
        Page<Incident> incidents;

            if ("createdByMe".equalsIgnoreCase(filter)) {
            incidents = incidentRepository.findByCreatedBy(userId, sortedPageable);

        } else if ("assignedToMe".equalsIgnoreCase(filter)) {
            incidents = incidentRepository.findByAssignedTo(userId, sortedPageable);

        } else if ("assignedByMe".equalsIgnoreCase(filter)) {

            if (!isAdmin && !isSupervisor) {
                throw new ForbiddenException("FILTER_NOT_ALLOWED",
                        "The 'assignedByMe' filter is only available to admins and supervisors.");
            }

            incidents = incidentRepository.findByAssignedBy(userId, sortedPageable);

            }


            // 5. if no filter is applied  then all the incident is shown to user based on his  ROLE

        else {
                incidents = incidentRepository.findIncidentsForUser(
                        userId,
                        isAdmin,
                        isSupervisor,
                        isEngineer,
                        teamEngineerIds,
                        sortedPageable
                );
            }
        // 5. Map to DTO
        return incidents.map(incident -> IncidentResponseDTO.builder()
                .id(incident.getId())
                .title(incident.getTitle())
                .severity(incident.getSeverity())
                .priority(incident.getPriority())
                .createdBy(incident.getCreatedBy())
                .assignedTo(incident.getAssignedTo())
                .assignedBy(incident.getAssignedBy())
                .incidentStatus(incident.getIncidentStatus())
                .build());



    }

    public IncidentResponseDTO getIncidentByIdBasedOnRole(UUID incidentId, Authentication auth) {

        UUID userId = UUID.fromString(auth.getName());

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("INCIDENT_NOT_FOUND",
                        "Incident not found: " + incidentId));

        IncidentResponseDTO response = IncidentResponseDTO.builder()
                 .id(incident.getId())
                .title(incident.getTitle())
                .severity(incident.getSeverity())
                .priority(incident.getPriority())
                .createdBy(incident.getCreatedBy())
                .assignedTo(incident.getAssignedTo())
                .assignedBy(incident.getAssignedBy())
                .incidentStatus(incident.getIncidentStatus())
                .build();

        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // ADMIN → full access
        if (roles.contains("ROLE_ADMIN")) {
            return response;
        }

        // SUPERVISOR
        if (roles.contains("ROLE_SUPERVISOR")) {

            List<UUID> teamEngineerIds = userClient.getEngineersUnderSupervisor(userId);

            boolean isOwnIncident =
                    incident.getCreatedBy().equals(userId) ||
                            (incident.getAssignedBy() != null && incident.getAssignedBy().equals(userId));

            boolean isTeamIncident =
                    incident.getAssignedTo() != null &&
                            teamEngineerIds.contains(incident.getAssignedTo());

            if (isOwnIncident || isTeamIncident) {
                return response;
            }
        }

        // ENGINEER
        if (roles.contains("ROLE_ENGINEER")) {

            boolean isAllowed =
                    incident.getCreatedBy().equals(userId) ||
                            (incident.getAssignedTo() != null && incident.getAssignedTo().equals(userId));

            if (isAllowed) {
                return response;
            }
        }

        log.warn("User {} not authorized for incident {}", userId, incidentId);
        throw new ForbiddenException("INCIDENT_ACCESS_DENIED",
                "You do not have access to this incident.");
    }


    public UpdateIncidentMetadate_ResponseDTO updateIncidentBasedOnRole(
            UUID incidentId,
            UpdateIncidentMetadate_RequestDTO request,
            Authentication auth
    ) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("INCIDENT_NOT_FOUND",
                        "Incident not found: " + incidentId));

        UUID userId = UUID.fromString(auth.getName());

        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isSupervisor = roles.contains("ROLE_SUPERVISOR");
        boolean isEngineer = roles.contains("ROLE_ENGINEER");

        boolean allowed = false;

        // ADMIN
        if (isAdmin) {
            allowed = true;
        }

        // SUPERVISOR
        else if (isSupervisor) {

            List<UUID> teamEngineerIds = userClient.getEngineersUnderSupervisor(userId);

            boolean isOwnIncident =
                    incident.getCreatedBy().equals(userId) ||
                            (incident.getAssignedBy() != null && incident.getAssignedBy().equals(userId));

            boolean isTeamIncident =

                            (incident.getAssignedTo() != null && teamEngineerIds.contains(incident.getAssignedTo())) || (teamEngineerIds.contains(incident.getCreatedBy()) );

            allowed = isOwnIncident || isTeamIncident;
        }

        // ENGINEER
        else if (isEngineer) {
            boolean isCreator = incident.getCreatedBy().equals(userId);
            allowed = isCreator;
        }

        if (!allowed) {
            throw new ForbiddenException("INCIDENT_UPDATE_DENIED",
                    "You are not allowed to update this incident.");
        }

        // Apply updates
       Incident updatedIncident= applyMetadataUpdatesToIncident(incident, request);

        // Update timestamp
        updatedIncident.setUpdatedAt(LocalDateTime.now());

        // Save once
        Incident updated = incidentRepository.save(updatedIncident);

        // Return response
        return UpdateIncidentMetadate_ResponseDTO.builder()
                .id(updated.getId())
                .updatedTitle(updated.getTitle())
                .updatedDescription(updated.getDescription())
                .updatedSeverity(updated.getSeverity())
                .updatedPriority(updated.getPriority())
                .updatedAffectedSource(updated.getAffectedSource())
                .updatedEnvironment(updated.getEnvironment())
                .updatedLocation(updated.getLocation())
                .updatedAt(updated.getUpdatedAt())
                .build();
    }


    //Helper method to apply updates to the incident entity based on the request DTO
    private Incident applyMetadataUpdatesToIncident(Incident incident, UpdateIncidentMetadate_RequestDTO request) {
        if (request.getTitle() != null && ! request.getTitle().isBlank()) incident.setTitle(request.getTitle());
        if (request.getDescription() != null && !request.getDescription().isBlank()) incident.setDescription(request.getDescription());
        if (request.getSeverity() != null ) incident.setSeverity(request.getSeverity());
        if (request.getPriority() != null) incident.setPriority(request.getPriority());
        if (request.getAffectedSource() != null && !request.getAffectedSource().isBlank()) incident.setAffectedSource(request.getAffectedSource());
        if (request.getEnvironment() != null) incident.setEnvironment(request.getEnvironment());
        if (request.getLocation() != null && !request.getLocation().isBlank()) incident.setLocation(request.getLocation());
return incident;
    }


    public UpdateIncidentStatus_ResponseDTO updateIncidentStatusBasedOnRole(UUID incidentId, UpdateIncidentStatus_RequestDTO request, Authentication auth) {
        //1.firstly we are checking is the User who try to update the incident status is Active or not
        UUID StatusUpdaterId=UUID.fromString(auth.getName());

        if (!IsUserActive(StatusUpdaterId)){
            log.warn("User with id {}: Status is Not active so not authorized to Update an incident Status.", StatusUpdaterId);
            throw new ForbiddenException("USER_NOT_ACTIVE",
                    "Your account is not ACTIVE, so you cannot update an incident status.");
        }

       Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("INCIDENT_NOT_FOUND",
                        "Incident not found: " + incidentId));

        UUID userId = UUID.fromString(auth.getName());


        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isSupervisor = roles.contains("ROLE_SUPERVISOR");
        boolean isEngineer = roles.contains("ROLE_ENGINEER");

        boolean allowed = false;

        // ADMIN
        if (isAdmin) {
            allowed = true;
        }

        // SUPERVISOR
        else if (isSupervisor) {

            List<UUID> teamEngineerIds = userClient.getEngineersUnderSupervisor(userId);

            boolean isOwnIncident =
                    incident.getCreatedBy().equals(userId) ||
                            (incident.getAssignedBy() != null && incident.getAssignedBy().equals(userId));

            boolean isTeamIncident =
                    (incident.getAssignedTo() != null && teamEngineerIds.contains(incident.getAssignedTo())) || (teamEngineerIds.contains(incident.getCreatedBy()) );

            allowed = isOwnIncident || isTeamIncident;
        }

        // ENGINEER
        else if (isEngineer) {
            boolean isCreatorOrAssignee =
                    incident.getCreatedBy().equals(userId) ||
                            (incident.getAssignedTo() != null && incident.getAssignedTo().equals(userId));

            allowed = isCreatorOrAssignee;
        }

        if (!allowed) {
            throw new ForbiddenException("INCIDENT_STATUS_UPDATE_DENIED",
                    "You are not allowed to update the status of this incident.");
        }

        // Update status
        String oldIncidentStatus=incident.getIncidentStatus().name();
        incident.setIncidentStatus(request.getStatus());
        incident.setUpdatedAt(LocalDateTime.now());
        // If the status is updated to RESOLVED then we need to set the root cause and resolutionNotes as well
        incident.setRootCause(request.getRootCause());
        incident.setResolutionNotes(request.getResolutionNotes());

        Incident updated = incidentRepository.save(incident);

//  if incident is resolved then we will publish an the IncidentResolvedEvent  that will be consumed by ai service only for storing in vector db
//        if(request.getStatus()==IncidentStatus.RESOLVED){
//            IncidentResolvedEvent resolvedEvent = IncidentResolvedEvent.builder()
//                    .incidentId(updated.getId())
//                    .eventId(UUID.randomUUID())
//                    .performedBy(StatusUpdaterId)
//                    .title(updated.getTitle())
//                    .description(updated.getDescription())
//                    .category(updated.getCategory()!=null?updated.getCategory().toString():null)
//                    .serviceName(updated.getServiceName()!=null?updated.getServiceName().name():null)
//                    .severity(updated.getSeverity()!=null?updated.getSeverity().name():null)
//                    .rootCause(updated.getRootCause())
//                    .resolutionNotes(updated.getResolutionNotes())
//                    .resolvedAt(LocalDateTime.now())
//                    .build();
//            log.info("Since this status is resolved so we are going to publish this Incident resolved Event for incidentId: {}  and incident title: {} ....to be consumed by Ai-Service", updated.getId(),updated.getTitle());
//
//            incidentEventProducer.PublishIncidentResolved(incidentId.toString(), resolvedEvent);
//        }

        //Now we publish UpdateIncidentStatus Event :
        IncidentStatusUpdatedEvent event=IncidentStatusUpdatedEvent
                .builder()
                .incidentId(updated.getId())
                .eventId(UUID.randomUUID())// used to uniquely identify the event and also used in Idempotency in case of retry mechanism , also used for tracing the event in distributed system(multiple Services)
                .performedBy(StatusUpdaterId)// this field is used to know who performed the action of creating the incident .
                .title(updated.getTitle())
                .description(updated.getDescription())
                .OldStatus(oldIncidentStatus)
                .UpdatedStatus(updated.getIncidentStatus().name())
                .createdBy(updated.getCreatedBy())
                .assignedBy(updated.getAssignedBy())
                .assignedTo(updated.getAssignedTo())
                .UpdatedAt(updated.getUpdatedAt())
                .UpdatedBy(StatusUpdaterId)
                .serviceName(updated.getServiceName()!=null?updated.getServiceName().name():null)
                .category(updated.getCategory()!=null?updated.getCategory().toString():null)
                .message("Incident status updated to "+updated.getIncidentStatus().name())
                .build();
        if(request.getStatus()==IncidentStatus.RESOLVED){
            event.setRootCause(updated.getRootCause());
            event.setResolutionNotes(updated.getResolutionNotes());
            event.setResolvedAt(updated.getUpdatedAt());
        }
log.info("Publishing IncidentStatusUpdatedEvent for incidentId: {} with new status: {}", updated.getId(), updated.getIncidentStatus().name());
        //2.publish IncidentStatusUpdated Event to Kafka Topic
        incidentEventProducer.PublishIncidentStatusUpdated(updated.getId().toString(),event);

        return UpdateIncidentStatus_ResponseDTO.builder()
                .id(updated.getId())
                .updatedStatus(updated.getIncidentStatus())
                .message("Incident status updated successfully")
                .build();
    }
}

