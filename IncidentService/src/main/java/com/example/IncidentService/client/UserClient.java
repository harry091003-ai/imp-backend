package com.example.IncidentService.client;

import com.example.IncidentService.dto.UserCredentialDTO;
import com.example.IncidentService.dto.UserValidationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * How this service asks UserService about a user.
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
 *     webClientBuilder.build().get().uri(...).retrieve().bodyToMono(...).block();
 *                                                                       ^^^^^^^
 *
 * WebClient is built for reactive apps - apps whose whole design is "never sit and wait".
 * Then .block() told it to sit and wait anyway. We were paying for machinery and then
 * throwing away the only thing it was for.
 *
 * IncidentService is a normal Spring MVC service. It gets one thread per request, and that
 * thread is MEANT to wait. So it should use a client that waits by design. Feign is that
 * client, and Ai-Service already uses it - so now all three of our waiting services use the
 * same tool.
 *
 * (The gateway still uses WebClient, and that is correct. The gateway really is reactive, so
 * it really does need the non-blocking client. The rule is: the client follows the app's
 * design, not personal taste.)
 *
 * ===========================================================================================
 * "WHERE IS THE CODE? THIS IS JUST AN INTERFACE WITH NO BODIES."
 *
 * That is the part that looks like magic, so here is what actually happens.
 *
 * At startup, @EnableFeignClients (on IncidentServiceApplication) tells Spring to find every
 * interface marked @FeignClient. For each one, Feign WRITES a class that implements it, and
 * puts that object in the Spring container.
 *
 * So when something injects UserClient, it does not get this interface - it gets Feign's
 * generated object. When you call getUser(id), that object reads the annotations below and
 * builds the HTTP request from them:
 *
 *     @GetMapping("/internal/validation/{userId}")   ->  make a GET to that path
 *     @PathVariable("userId") UUID userId            ->  put this argument into {userId}
 *     returns UserValidationDTO                      ->  read the JSON reply into this class
 *
 * You describe the call. Feign writes the calling code.
 *
 * ===========================================================================================
 * THE THREE THINGS THAT USED TO BE IN THIS FILE, AND WHERE THEY WENT
 *
 *   1. The headers (X-Internal-Secret, X-Internal-Service)
 *         -> config/FeignInternalAuthConfig.java
 *      Added automatically to every Feign call this service makes, so a new call added later
 *      cannot forget them.
 *
 *   2. The error handling (404 -> NotFoundException, anything else -> RuntimeException)
 *         -> config/FeignErrorDecoderConfig.java
 *      Same rule as before, written once instead of copied into each method.
 *
 *   3. The time limit
 *         -> application.properties, under spring.cloud.openfeign.client.config.*
 *      There was NO time limit before. That is the whole point of Track B3 - see the long
 *      note in that file.
 *
 * Each of those is now set once for the whole service, instead of per call. That is the real
 * benefit of the switch: it is not possible to add a new call that quietly forgets one.
 * ===========================================================================================
 */
// name = "USERSERVICE" is a EUREKA NAME, not a web address.
//
// There is no http://localhost:8084 written down anywhere here on purpose. At call time,
// Feign asks Eureka "who is USERSERVICE?" and Eureka answers with a real address. If
// UserService moves, or we run three copies of it, nothing in this file changes.
//
// It must match UserService's spring.application.name EXACTLY - it is "USERSERVICE"
// (checked, not assumed - our service names are inconsistently cased across this project).
@FeignClient(name = "USERSERVICE")
public interface UserClient {

    /**
     * Does this user exist, and are they active? Used before assigning an incident.
     *
     * A 404 here becomes a NotFoundException (see FeignErrorDecoderConfig), which common-web turns
     * into a proper 404 for our own caller. That behaviour is carried over from the old code
     * on purpose - "that user id does not exist" is the caller's mistake, not our failure,
     * so it must not become a 500.
     */
    @GetMapping("/internal/validation/{userId}")
    UserValidationDTO getUser(@PathVariable("userId") UUID userId);

    /**
     * The ids of every engineer reporting to this supervisor.
     *
     * WORTH NOTICING: the return type is just List<UUID>.
     *
     * The old WebClient version needed this awkward thing:
     *     .bodyToMono(new ParameterizedTypeReference<List<UUID>>() {})
     *
     * That was needed because Java erases generics at runtime - by the time the code runs,
     * "List<UUID>" is only "List", so WebClient had to be told separately what was inside it.
     *
     * Feign does not have that problem. It reads the return type off this interface's method
     * signature, which is not erased. So it already knows. One less strange line to explain.
     */
    @GetMapping("/internal/EngineersUnderSupervisor/{SupervisorId}")
    List<UUID> getEngineersUnderSupervisor(@PathVariable("SupervisorId") UUID SupervisorId);

    /**
     * A user's email and name.
     *
     * TWO WARNINGS ON THIS ONE, both carried over from the old code, neither fixed here:
     *
     *   1. NOTHING CALLS THIS. It was checked - no caller anywhere in IncidentService.
     *      Kept rather than deleted because deleting unused code is a separate decision, and
     *      this change is meant to be a like-for-like swap of HOW we call, not WHAT we call.
     *
     *   2. THE ENDPOINT ON THE OTHER SIDE IS BROKEN. UserService declares it as
     *      @GetMapping("/UserSpecificDetails/{userId}") but names the argument "userid".
     *      The names do not match, so the mapping cannot resolve. This is known issue #2 in
     *      CLAUDE.md and it is a UserService bug, not ours.
     *
     * So if you ever start using this method, fix UserService first - it will not work today.
     */
    @GetMapping("/internal/UserSpecificDetails/{userId}")
    UserCredentialDTO getUserCredentials(@PathVariable("userId") UUID userId);
}
