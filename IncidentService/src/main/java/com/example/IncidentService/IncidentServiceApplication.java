package com.example.IncidentService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

// ADDED 2026-09-09 (Track B3, step 2): @EnableFeignClients
//
// WHAT IT DOES
// It tells Spring: "go and look for interfaces marked @FeignClient, and build a working
// implementation for each one at startup."
//
// Without this line, our UserClient interface stays just an interface. Nothing implements it,
// so nothing can be injected, and the app fails to start with "no bean of type UserClient".
//
// It searches this class's package and everything below it, which covers our whole service.
@EnableFeignClients
@SpringBootApplication
public class IncidentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(IncidentServiceApplication.class, args);
	}

}
