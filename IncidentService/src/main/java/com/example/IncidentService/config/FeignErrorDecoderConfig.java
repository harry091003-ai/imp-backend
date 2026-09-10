package com.example.IncidentService.config;

import com.project.web.exception.NotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Decides what exception to throw when another service answers us with an error.
 *
 * ADDED 2026-09-09 (Track B3, step 2).
 *
 * ===========================================================================================
 * WHY THIS FILE HAD TO EXIST
 *
 * This is NOT new behaviour. The old WebClient code did exactly this, but written out inside
 * every method:
 *
 *     .onStatus(HttpStatusCode::isError,
 *               response -> response.statusCode() == HttpStatus.NOT_FOUND
 *                   ? Mono.error(new NotFoundException("USER_NOT_FOUND", ...))
 *                   : Mono.error(new RuntimeException(...)))
 *
 * If we had switched to Feign without adding this file, that rule would have silently
 * disappeared - and a 404 would have started coming back to our users as a 500. "The user id
 * you asked about does not exist" would have been reported as "our server is broken".
 *
 * That is the kind of thing a rewrite quietly loses. Worth naming, because the code still
 * compiled and still ran; only the meaning changed.
 * ===========================================================================================
 *
 * WHEN FEIGN CALLS THIS
 * Only when the other service actually REPLIED, with a status of 400 or above. Feign hands us
 * that reply and asks "what exception should I throw?"
 *
 * WHAT IT DOES *NOT* COVER - and this matters for Track B3
 * A timeout never reaches this code. If UserService never replies at all, there is no response
 * to look at, so Feign throws its own error before getting here. That still fails fast now
 * (which is the whole point of step 2), but it currently surfaces to our caller as a plain
 * 500 "INTERNAL_ERROR" rather than a clearer 503. Turning "the dependency is down" into a
 * proper 503 is deliberately left for Track B3 step 4, where fallbacks are handled properly
 * for the whole project at once instead of in one corner of it.
 *
 * WHY A PLAIN @Bean
 * It applies to every Feign client in this service, including any added later - so a new call
 * cannot accidentally get different error behaviour from the existing ones.
 *
 * ===========================================================================================
 * WHY THIS CLASS IS CALLED ...Config AND NOT JUST "FeignErrorDecoder"
 *
 * It was called FeignErrorDecoder at first, and the service refused to start:
 *
 *     The bean 'feignErrorDecoder' ... could not be registered.
 *     A bean with that name has already been defined and overriding is disabled.
 *
 * The reason is a naming rule that is easy to walk into. When Spring finds a @Configuration
 * class by scanning, it registers THE CLASS ITSELF as a bean, and names it after the class
 * with the first letter lowercased:
 *
 *     class FeignErrorDecoder          ->  a bean named "feignErrorDecoder"
 *     @Bean ErrorDecoder feignErrorDecoder()  ->  a bean named "feignErrorDecoder"
 *
 * Two different beans, one name. Spring Boot refuses to guess which one you meant, so it
 * stops rather than silently picking one.
 *
 * THE RULE TO REMEMBER: a @Configuration class must never share its name with a @Bean method
 * inside it. Naming the class after the thing it PRODUCES is what causes the clash - naming
 * it after its JOB (configuring something) avoids it. That is why the sibling file
 * FeignInternalAuthConfig never had this problem: its method is internalAuthInterceptor, a
 * different word entirely.
 * ===========================================================================================
 */
@Configuration
public class FeignErrorDecoderConfig {

    @Bean
    public ErrorDecoder feignErrorDecoder() {

        // Feign's own decoder. We fall back to it for anything we do not have a special rule
        // for. It produces a FeignException that carries the status code AND the response
        // body, which is strictly more useful for debugging than the bare
        // RuntimeException("UserService returned error: 500") the old code threw.
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

        return (methodKey, response) -> {

            // 404 = "the thing you asked about does not exist".
            //
            // This is the caller's mistake, not our failure, so it must stay a 404 all the way
            // back to them. If we let it become a 500, we would be telling the user "our
            // server is broken" when actually they asked for a user id that is not there - and
            // we would also be filling our own error logs with alarms that need no action.
            if (response.status() == HttpStatus.NOT_FOUND.value()) {
                return new NotFoundException(
                        "USER_NOT_FOUND",
                        "User not found (asked " + response.request().url() + ")");
            }

            // Everything else - 400, 403, 500, and so on - is a real problem on our side or
            // the other service's side. It becomes a FeignException, which common-web's
            // GlobalExceptionHandler turns into a 500 INTERNAL_ERROR for our caller.
            //
            // A note on 403: if you ever see one of these from UserService, the usual cause is
            // that the internal headers did not arrive - check FeignInternalAuthConfig and
            // that internal.secret matches on both sides.
            return defaultDecoder.decode(methodKey, response);
        };
    }
}
