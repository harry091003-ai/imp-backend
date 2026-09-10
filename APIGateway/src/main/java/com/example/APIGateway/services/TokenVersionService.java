package com.example.APIGateway.services;

import com.example.APIGateway.utils.FailureDescription;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.logging.Logger;


// service used for handleing  the interservice communication using webclient : for getting the tokenversion from authservice
@Service
public class TokenVersionService {
    private final WebClient webClient;
    private static final Logger logger = Logger.getLogger(TokenVersionService.class.getName());

    public TokenVersionService(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("http://AUTHSERVICE").build();
        logger.info("✅ TokenVersionService initialized with LoadBalanced WebClient");
    }

    // Mono is used it make return type async... as we are retuning webclient which is reactive
    public Mono<Integer> getTokenVersion(String userid) {
        logger.info("🔍 Fetching token version for user: " + userid);

        return webClient.get()
                .uri("/auth/token-version/{userid}", userid)
                .retrieve() //we retrieve ethe response
                //This is basically use to handle the error 1st parameter check is there is status error??, 2nd parameter wil return error message if 1st param is true.
                .onStatus(status -> status.isError(),

                        response -> {
                            logger.severe("❌ AuthService returned error status: " + response.statusCode());
                            return Mono.error(new RuntimeException("Auth service error"));
                        })
                .bodyToMono(Integer.class) // wrap the resopnse with MONO  ie. MONO<1>
                .doOnSuccess(version -> logger.info("✅ Token version retrieved: " + version))
                // CHANGED 2026-09-09: was error.getMessage(), which printed the single word
                // "null" whenever the response timeout fired - because Netty's
                // ReadTimeoutException is built with no message on purpose. See
                // FailureDescription for the full explanation; the short version is that the
                // exception TYPE was the only information available, and we were not printing
                // it. Now we print the type, the whole cause chain, AND which timeout setting
                // is responsible.
                .doOnError(error -> logger.severe("❌ Error fetching token version for user "
                        + userid + ": " + FailureDescription.of(error)));
    }
}


