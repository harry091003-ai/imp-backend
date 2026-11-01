package com.example.Ai_Service.feignClients;

import com.example.Ai_Service.dto.UserValidationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

// AI plan phase 1: who is in a supervisor's team, and who can really be assigned. The name must match UserService's
// spring.application.name exactly. The internal headers come from FeignInternalAuthConfig.
@FeignClient(name = "USERSERVICE")
public interface UserServiceFeignClient {

    @GetMapping("/internal/EngineersUnderSupervisor/{SupervisorId}")
    List<UUID> getEngineersUnderSupervisor(@PathVariable("SupervisorId") UUID supervisorId);

    @PostMapping("/internal/validation/batch")
    List<UserValidationDTO> validateUsers(@RequestBody List<UUID> userIds);
}
