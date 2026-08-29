# 🚀 JwtGatewayFilter – Complete Notes (Clean & Practical)

---
This Is ApiGateway Service:

Instead of 
1.calling  Api endpoints of each Microservice separately,by there specific port number. \
2 handling  authentication of each Microservice separately .

we are handling it centeraly  that why we are using this ApiGateway microservice.

As by using this ApiGateway microservice:
1.we are making a single entry point for calling all the microservices( by using the apiGateway port number )


Our JWTGatewayFilter.java file: this the entry point of all the api's Requests:
this ApiGateway filter is based on Sping WebFlux not on spring MVC.
so it uses the WebFlux Filter Like GlobalFilter,Gateway Filter...not the serverlet filter provided by spring security.

Difference in WebFlux Filters and servelet filters
WebFlux Filters are a>Reactive behaviour: Non Blocking+Async+Event Driven
                    b> Runs in WebFlux(Netty/undertow)
                    c> Here few threads Handle many requests as it uses the event-loop(small number of threads that keep checking and processing events continuously) as thread does not need to wait for response
                    d> its filters return type is Mono<void>
                    e> For send/receiving request/response it uses ServerWebExchange 
                    f>request->Eventloop-> asyn processing->response
Servelet Filter:    a> Blocking(sync)behaviour
                    b> Runs in Spring MVC(Tomcat)
                    c> as sync so each thread per request
                    d> its filter return type is void
                    e> For send/receiving request/response it uses HttpServletRequest/Response
                    f>Request->Thread assign->work->response->Thread Released

flow:
1. Request comes to Gateway
2. Check if  Api is public means no authentication needed ,if yes then directly send that request to that microservice eg: api's start from auth is public api 
3. For remaining Api's they Check Authorization header
4. Extract token
5. Validate token (expiry + signature)
6. Extract userid,email, roles, tokenVersion from token
7. Call AuthService using webclient →to get latest version from db
8. Compare both versions (coming from token v/s db)
9. If version valid  successfully → then add headers (contains info of userid,email, roles, tokenVersion )to  the api request
10. Forward request to that particular microservice.
11. Else → 401

for Identifying each microservice ,using there ApiEndpoints :
we are Adding the id, uri and predicate of each microservice in the application.properties file.

for getting the token version from db we are using webclient

