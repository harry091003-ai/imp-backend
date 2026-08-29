package com.example.IncidentService.client;

import com.example.IncidentService.dto.EngineersIdDTO;
import com.example.IncidentService.dto.UserCredentialDTO;
import com.example.IncidentService.dto.UserValidationDTO;
import com.project.web.exception.NotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserClient {

    private final WebClient.Builder webClientBuilder;

    public UserClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;

    }

    //till the we .bodytoMono() the request is not executed yet.
//    use of .block()
//    1. when we Right the .block() then the request is executed and we get the response in a blocking way
//     2. .block() is used to convert the asynchronous call(as webclient is lazy(asyn))  to a synchronous (blocking) call.
//        if we are not using block()
//        1. then webflux will automatically call the .subscribe() method and it will execute the request (in lazy way means not at that point) and return a Mono<UserValidationDTO> instead of UserValidationDTO
    public UserValidationDTO getUser(UUID userId) {
        String UserIdString = userId.toString();
        return webClientBuilder.build().get()
                .uri("http://USERSERVICE/internal/validation/{userId}", userId)//Prepare endpoint
                .retrieve()//Prepare to fetch response
                .onStatus(HttpStatusCode::isError,//Define error handling (but not executed yet)
                        // A 404 from UserService means "this user id does not exist" - that is a
                        // client mistake (404), not a server failure. Anything else really is a
                        // dependency problem and is left to bubble up as a 500.
                        response -> response.statusCode() == HttpStatus.NOT_FOUND
                                ? Mono.error(new NotFoundException("USER_NOT_FOUND", "User not found: " + userId))
                                : Mono.error(new RuntimeException("UserService returned error: " + response.statusCode())))
                .bodyToMono(UserValidationDTO.class)//Define how to convert response → MONO<DTO>
                .block();//it trigger the request and return the synchronous response
    }

    public List<UUID> getEngineersUnderSupervisor(UUID SupervisorId) {
//        String UserIdString = SupervisorId.toString();
//        String roles = auth.getAuthorities().stream()
//                .map(GrantedAuthority::getAuthority)
//                .collect(Collectors.joining(","));
        return webClientBuilder.build().get()
                .uri("http://USERSERVICE/internal/EngineersUnderSupervisor/{SupervisorId}", SupervisorId)//Prepare endpoint
//                .header("X-User-Id", UserIdString)
//                .header("X-Roles", roles)
                .retrieve()//Prepare to fetch response
                .onStatus(HttpStatusCode::isError,//Define error handling (but not executed yet)
                        response -> Mono.error(new RuntimeException("UserService returned error: " + response.statusCode())))
                .bodyToMono((new ParameterizedTypeReference<List<UUID>>() {
                }))//Define how to convert response → MONO<DTO>
                .block();//it trigger the request and return the synchronous response
    }

// for getting the UserCredential(mainly Email: used by Notification service for sending email)using Sync Interservice Communication
    public UserCredentialDTO getUserCredentials(UUID userId) {
//        String UserIdString = userId.toString();
//        String roles = auth.getAuthorities().stream()
//                .map(GrantedAuthority::getAuthority)
//                .collect(Collectors.joining(","));

        return webClientBuilder.build().get()
                .uri("http://USERSERVICE/internal/UserSpecificDetails/{userId}", userId)//Prepare endpoint
//                .header("X-User-Id", UserIdString)
//                .header("X-Roles", roles)
                .retrieve()//Prepare to fetch response
                .onStatus(HttpStatusCode::isError,//Define error handling (but not executed yet)
                        response -> response.statusCode() == HttpStatus.NOT_FOUND
                                ? Mono.error(new NotFoundException("USER_NOT_FOUND", "User not found: " + userId))
                                : Mono.error(new RuntimeException("UserService returned error: " + response.statusCode())))
                .bodyToMono(UserCredentialDTO.class)
                .block();

    }
}



