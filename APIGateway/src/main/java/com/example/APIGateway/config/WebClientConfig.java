package com.example.APIGateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;


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

    // ===================================================================================
    // ADDED 2026-09-09 (Track B3, step 1): timeouts and a bounded connection pool.
    //
    // THE PROBLEM WE ARE FIXING
    //
    // Before this change, this WebClient had NO time limit of any kind. If AuthService
    // answered the phone but then went quiet, we would wait for it forever.
    //
    // People usually think that means "threads pile up". In a reactive app that is NOT
    // what happens - a thread waiting for a reply is released, not held. The real damage
    // is different, and worse:
    //
    //   1. WebClient keeps a POOL of open connections to reuse. It is small by default -
    //      roughly (cpu cores x 2), so about 16 on a normal laptop. Not hundreds.
    //   2. A call with no time limit borrows a connection and never gives it back.
    //   3. After ~16 slow calls the pool is empty. Permanently.
    //   4. Every new request then queues waiting for a free connection - and the default
    //      wait is 45 SECONDS - then fails.
    //   5. The gateway is now broken until someone restarts it. Even if AuthService
    //      recovers completely, those connections are still stuck in a wait with no end,
    //      so the gateway does NOT heal itself.
    //
    // That last point is the important one. Without a timeout, this is not "slow
    // sometimes". It is a permanent outage triggered by a temporary hiccup.
    //
    // WHY THE NUMBERS LIVE IN application.properties AND NOT HERE
    //
    // Timeouts are things you tune after watching real traffic, and you should be able to
    // change them without rebuilding the app. There is deliberately NO default value in
    // the @Value below - so the number exists in exactly ONE place. If we put a default
    // here too, anyone reading the code would have to work out which of the two wins.
    // One value, one place, no guessing.
    // ===================================================================================

    /** How long we wait to even reach AuthService. "Nobody answered the door." */
    @Value("${gateway.httpclient.connect-timeout-ms}")
    private int connectTimeoutMs;

    /**
     * How long we wait for AuthService to reply AFTER it picked up.
     * "They answered the door but never spoke."
     *
     * This is THE line that fixes the bug described above. A connect timeout alone does
     * not help: the hung calls we are worried about connect perfectly fine, then go quiet.
     */
    @Value("${gateway.httpclient.response-timeout-ms}")
    private int responseTimeoutMs;

    /** How many connections we are willing to keep open to any one service at a time. */
    @Value("${gateway.httpclient.max-connections}")
    private int maxConnections;

    /** How many requests may stand in line waiting for a free connection. */
    @Value("${gateway.httpclient.pending-acquire-max-count}")
    private int pendingAcquireMaxCount;

    /** How long a request will stand in that line before giving up. */
    @Value("${gateway.httpclient.pending-acquire-timeout-ms}")
    private int pendingAcquireTimeoutMs;

    /** How long an unused connection is kept before we close it. See the note below. */
    @Value("${gateway.httpclient.max-idle-time-ms}")
    private int maxIdleTimeMs;

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {

        // ---------------------------------------------------------------------------
        // THE CONNECTION POOL
        //
        // Opening a fresh TCP connection for every call is slow, so WebClient keeps a
        // handful open and reuses them. This is that pool.
        //
        // We are replacing the built-in one for two reasons:
        //   - the default is shared globally across the whole app, so we cannot see or
        //     control it properly
        //   - its default "wait in line" time is 45 seconds, which is far too long for
        //     something that sits on EVERY single request
        // ---------------------------------------------------------------------------
        ConnectionProvider connectionProvider = ConnectionProvider.builder("gateway-pool")

                // The most connections we will hold open. Once these are all busy, new
                // requests wait in line (see the next two settings).
                .maxConnections(maxConnections)

                // How many are allowed to wait in that line. Beyond this, a request is
                // rejected straight away.
                //
                // Rejecting fast is deliberate and is better than it sounds. A queue with
                // no limit does not "handle more load" - it just grows in memory until the
                // app dies, while every user in the queue waits for a reply that is far
                // too late to be useful anyway. A quick honest "no" is kinder than a slow
                // maybe.
                .pendingAcquireMaxCount(pendingAcquireMaxCount)

                // How long a request waits in that line before giving up.
                // The default here is 45 seconds. That is the single worst default in this
                // whole area - a user would sit staring at a loading spinner for 45s
                // before even finding out we could not get a connection to try with.
                .pendingAcquireTimeout(Duration.ofMillis(pendingAcquireTimeoutMs))

                // Close a connection that has been sitting unused for this long.
                //
                // This one is not about speed - it prevents a confusing random error.
                // The server at the other end also closes idle connections after a while
                // (Tomcat does it at about 20 seconds). If it closes one and we do not
                // know, our next call grabs that dead connection and fails with
                // "Connection prematurely closed BEFORE response".
                //
                // It looks random and it is horrible to debug. The fix is simply to let go
                // of our connections BEFORE the other side does - so our number here must
                // stay comfortably lower than theirs.
                .maxIdleTime(Duration.ofMillis(maxIdleTimeMs))

                // Close a connection once it is this old, even if it is busy being reused. ↑ Force-close after X seconds/minutes, no exceptions , ↑ Don't wait for it to become idle, kill it even mid-use.
                //Stops one connection living forever and quietly pinning us to one ↑ Without this, a connection could live for days/weeks/forever ↑ And meanwhile, the load balancer moved on but we didn't notice.
                //instance of AuthService after the load balancer has moved on.   ↑ Load balancer: "use instance #2 now"   ↑ Gateway: "but I have this nice connection to instance #1..."   ↑ Result: gateway fights the load balancer's decision
                .maxLifeTime(Duration.ofMinutes(5))

                // Actually go and clean up expired connections on a timer.
                // Without this, expired connections are only noticed when someone next
                // tries to use one - so a quiet period leaves dead connections lying
                // around, and the first request after the quiet period pays for it.
                .evictInBackground(Duration.ofSeconds(30))

                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)

                // Time limit for "can I reach you at all".
                // This is a raw Netty setting, which is why it looks different from the
                // line below it - Netty is the networking library underneath WebClient.
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)

                // Time limit for "you picked up, now say something".
                // This is the one that stops connections leaking forever.
                .responseTimeout(Duration.ofMillis(responseTimeoutMs));

        // WHAT HAPPENS WHEN A TIMEOUT FIRES?
        //
        // Netty throws an exception, which travels back through the Mono. JwtGatewayFilter
        // already catches it in its .onErrorResume(...) block and turns it into a 503
        // "Unable to verify the session right now."
        //
        // So no change is needed in that filter. It was already written to handle
        // "AuthService failed" - it just never had a way to find out, because the call
        // never actually failed. It only hung. Now it fails, and the handling that was
        // already there starts doing its job.



//        Your code (Spring WebClient)
//        ↓
//        ↓ (speaks Spring language)
//        ↓
//ReactorClientHttpConnector  ← THE TRANSLATOR
//        ↓
//        ↓ (speaks Netty language)
//        ↓
//Netty (the actual networking library that makes HTTP calls)
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
