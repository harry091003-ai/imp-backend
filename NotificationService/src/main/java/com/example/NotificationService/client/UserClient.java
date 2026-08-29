package com.example.NotificationService.client;

import com.example.NotificationService.dto.UserCredentialDTO;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

// this service is used to call the incident service.
@Service
public class UserClient {
    private final WebClient.Builder webClientBuilder;

    public UserClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;

    }


    // for getting the UserCredential(mainly Email: used by Notification service for sending email)using Synchronous Interservice Communication
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
                .onStatus(status -> status.isError(),//Define error handling (but not executed yet)
                        response -> Mono.error(new RuntimeException("UserService returned error: " + response.statusCode())))
                .bodyToMono(UserCredentialDTO.class)
                .block();

    }



    // for getting set of UserCredential(mainly Email: used by Notification service for sending email) using Synchronous Interservice Communication
public List<UserCredentialDTO>getBatchOfUsers_Credentials(List<UUID> userid){

     return   webClientBuilder.build()
                .post()
                .uri("http://USERSERVICE/internal/UserSpecificDetails/batch")
                .bodyValue(userid)
                .retrieve()//Prepare to fetch response
                .onStatus(status -> status.isError(),//Define error handling (but not executed yet)
                        response -> Mono.error(new RuntimeException("UserService returned error: " + response.statusCode())))
                .bodyToMono(new ParameterizedTypeReference<List<UserCredentialDTO>>() {})// we use parameterized Type Reference because as we are using Set/List with is not understandable at run time without this
                .block();

}

}

