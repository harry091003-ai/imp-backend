package com.example.IncidentService.controller;

import com.example.IncidentService.dto.*;
import com.example.IncidentService.service.IncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
public class IncidentController {
    private final IncidentService service;

    @PostMapping
    public ResponseEntity<?> createIncident(@Valid @RequestBody CreateIncidentRequestDTO requestDTO ,  Authentication auth)
    {
        String userid=auth.getName();
        return ResponseEntity.ok(service.CreateIncident(UUID.fromString(userid),requestDTO));

    }

//assigned by supervisor or Admin  to engineer
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERVISOR')")
    @PutMapping("/{incident_id}/assign")
    public ResponseEntity<AssignIncidentResponseDTO> assignIncidentToEngineer(@PathVariable("incident_id") UUID incidentId, @Valid @RequestBody AssignIncidentRequestDTO request,Authentication auth)
    {
        AssignIncidentResponseDTO responseDTO=  service.AssignIncident(incidentId,request.getEnginerId(), auth);
        return ResponseEntity.ok(responseDTO);
    }

    //get list of incidents
    // 1.based on roles with
    // 2. also pagination is present
    // 3. also add the filter to get if u want only Incidents: 1. createdByMe 2. assignedToMe 3.assignedByMe
    @GetMapping
    public ResponseEntity<Page<IncidentResponseDTO>> getIncidents(Pageable pageable ,Authentication auth, @RequestParam(required = false)String filter){
        String userid=auth.getName();
        Page<IncidentResponseDTO> responseDTO=  service.getIncidentsBasedOnRoles(UUID.fromString(userid), auth, pageable,filter
        );
        return ResponseEntity.ok(responseDTO);
    }


    //get specific incident iF u have the access
    @GetMapping("/{incident_id}")
    public ResponseEntity<IncidentResponseDTO> getIncidentById(@PathVariable("incident_id") UUID incidentId,Authentication auth){
        IncidentResponseDTO incident =service.getIncidentByIdBasedOnRole(incidentId,auth);

       return ResponseEntity.ok(incident);}

//Updating the MetaData of Incident With Id
//    Role	Access
//    ADMIN	:Full MetaData
//    SUPERVISOR:	Own / team (full MetaData)
//    ENGINEER creator	:Full MetaData
    @PutMapping("{incident_id}")
    public ResponseEntity<UpdateIncidentMetadate_ResponseDTO>updateIncidentById(@PathVariable("incident_id") UUID incident_Id,@Valid @RequestBody UpdateIncidentMetadate_RequestDTO request, Authentication auth)
    { UpdateIncidentMetadate_ResponseDTO response=service.updateIncidentBasedOnRole(incident_Id,request,auth);
       return ResponseEntity.ok(response);
    }

    //Updating the MetaData of Incident With Id
//    Role	Access
//    ADMIN	:yes
//    SUPERVISOR:	Own / team (yes)
//    ENGINEER creator /assigned	:yes
    @PutMapping("/{incident_id}/status")
    public ResponseEntity<UpdateIncidentStatus_ResponseDTO> updateIncidentStatusById(@PathVariable("incident_id") UUID incidentId, @Valid @RequestBody UpdateIncidentStatus_RequestDTO request, Authentication auth) {
        UpdateIncidentStatus_ResponseDTO response=service.updateIncidentStatusBasedOnRole(incidentId, request, auth);
        return ResponseEntity.ok(response);
    }
}
