package com.example.NotificationService.client;

import com.example.NotificationService.dto.UserCredentialDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/**
 * How this service looks up the email addresses it needs to notify people.
 *
 * ===========================================================================================
 * CHANGED 2026-09-09 (Track B3, step 2): this was a normal class using WebClient. It is now a
 * Feign interface. Nothing that CALLS it had to change - same method names, same arguments,
 * same return types.
 *
 * WHY WE CHANGED IT
 *
 * The old code did this on every call:
 *
 *     webClientBuilder.build().post().uri(...).retrieve().bodyToMono(...).block();
 *                                                                        ^^^^^^^
 *
 * WebClient is built for reactive apps - apps whose whole design is "never sit and wait".
 * Then .block() told it to sit and wait anyway. We were paying for machinery and then
 * throwing away the only thing it was for.
 *
 * NotificationService is a normal Spring MVC service, and this client is called from a Kafka
 * listener - both are ordinary threads that are MEANT to wait. So they should use a client
 * that waits by design. Feign is that client, and Ai-Service already uses it.
 *
 * WHY IT MATTERED MORE HERE THAN IT LOOKS
 * These calls had no time limit at all. A thread calling a frozen UserService would wait
 * forever and never come back. With enough of them, every thread in the pool is stuck and the
 * service is completely dead - while still reporting itself as healthy, because nothing
 * crashed. It just quietly stopped doing anything.
 *
 * ===========================================================================================
 * "WHERE IS THE CODE? THIS IS JUST AN INTERFACE WITH NO BODIES."
 *
 * At startup, @EnableFeignClients (on NotificationServiceApplication) tells Spring to find
 * every interface marked @FeignClient. For each one, Feign WRITES a class that implements it
 * and puts that object in the Spring container.
 *
 * So when something injects UserClient, it gets Feign's generated object, not this interface.
 * When you call a method, that object reads the annotations below and builds the HTTP request
 * from them. You describe the call; Feign writes the calling code.
 *
 * ===========================================================================================
 * THE THREE THINGS THAT USED TO BE IN THIS FILE, AND WHERE THEY WENT
 *
 *   1. The headers (X-Internal-Secret, X-Internal-Service)
 *         -> config/FeignInternalAuthConfig.java
 *
 *   2. The error handling
 *         -> deleted, because Feign's built-in handling is better than what we had.
 *
 *      The old code turned EVERY error into the same thing:
 *          new RuntimeException("UserService returned error: " + statusCode)
 *      A status code and nothing else.
 *
 *      Feign's default gives you the status code, the method, the full URL, AND the response
 *      body. When one of these ends up parked in a dead letter topic, that difference is the
 *      difference between knowing what happened and guessing.
 *
 *      (IncidentService DOES have a FeignErrorDecoderConfig, and that is not an inconsistency - its
 *      old code had a real rule worth keeping: 404 means "no such user", which has to reach
 *      the waiting web user as a 404 and not a 500. Nothing is waiting here, and the old code
 *      here had no such rule, so there was nothing to carry over.)
 *
 *   3. The time limit
 *         -> application.properties, under spring.cloud.openfeign.client.config.*
 *      (there was none before - that is the point of Track B3)
 *
 * Set once for the whole service instead of per call, so a call added later cannot forget one.
 * ===========================================================================================
 */
// name = "USERSERVICE" is a EUREKA NAME, not a web address.
// At call time Feign asks Eureka "who is USERSERVICE?" and gets a real address back. Must
// match UserService's spring.application.name exactly - checked, it is "USERSERVICE".
@FeignClient(name = "USERSERVICE")
public interface UserClient {

    /**
     * One user's email and name.
     *
     * TWO WARNINGS, both carried over from the old code, neither fixed here:
     *
     *   1. NOTHING CALLS THIS. Checked - the only method actually used is the batch one below.
     *      Kept rather than deleted because removing unused code is a separate decision; this
     *      change is a like-for-like swap of HOW we call, not WHAT we call.
     *
     *   2. THE ENDPOINT ON THE OTHER SIDE IS BROKEN. UserService declares it as
     *      @GetMapping("/UserSpecificDetails/{userId}") but names the argument "userid". The
     *      names do not match, so the mapping cannot resolve. Known issue #2 in CLAUDE.md,
     *      and it is a UserService bug, not ours.
     */
    @GetMapping("/internal/UserSpecificDetails/{userId}")
    UserCredentialDTO getUserCredentials(@PathVariable("userId") UUID userId);

    /**
     * Email addresses for a whole list of users in one call. This is the one actually used.
     *
     * WHY POST FOR SOMETHING THAT ONLY READS DATA?
     * Because we send a list of user ids, and a list does not fit sensibly in a URL. GET
     * requests are not supposed to carry a body - many clients and gateways silently drop it.
     * So POST is used here as "a read that needs a body", which is a normal and accepted
     * exception to the rule. (The same trap is a live bug elsewhere in this project - see
     * known issue #7, RagController using GET with a body.)
     *
     * TWO SMALL THINGS THE OLD CODE NEEDED AND THIS DOES NOT:
     *
     *   - .bodyValue(userid)  ->  now just an argument marked @RequestBody
     *   - new ParameterizedTypeReference<List<UserCredentialDTO>>() {}
     *         -> now just the return type
     *
     * That second one existed because Java erases generics at runtime: "List<UserCredentialDTO>"
     * becomes plain "List" by the time the code runs, so WebClient had to be told separately
     * what was inside the list. Feign reads it off this method signature instead, which is not
     * erased - so it already knows.
     */
    @PostMapping("/internal/UserSpecificDetails/batch")
    List<UserCredentialDTO> getBatchOfUsers_Credentials(@RequestBody List<UUID> userid);
}
