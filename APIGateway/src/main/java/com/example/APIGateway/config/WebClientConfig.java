package com.example.APIGateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;


//Spring Webclient: is used to call external APis or microservice from your application.
// is  a reactive HTTP client as it is provided by spring webFlux.
// and as we know webflux is reactive (Non Blocking+Async+Event Driven)
//As we are also using webflux for making the JWTApiGateway Filter....and the JWTApiGateway actually call the authservice (for getting the token version) so the way we are  calling authservice must also be reactive.

// we are using Loadbalancing here: because  there can be multiple instance of authservice that are registered in eureka so it will automatically handle enables client-side load balancing with Eureka service discovery by distributing the load .

//RestTemplate: is also used to call external APis or microservice from your application...
// But its Sync way (Blocking)
//so if we use it with JWTApiGateway then it will  Block the threads and evently block the eventloop.
// so it is only used when we are using the servelet-filters or in non reactive microservices

@Configuration
public class WebClientConfig {
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}

