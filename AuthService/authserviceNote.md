Track of work:
version1:This architecture is followed by me before in my using the ApiGateway microservice:


as we are using the common-security module for the follwing works
Authservice:

in Authservice service we can :
 1. do register the user
 2. login the user and finally generate the JWT token for the user (this token generation function is used from the common-security module's JWTutil class)
 3. for updating the user details (like password,username,email) we  need to authenticate the user so we use authentication from common-security module
keep in mind we just add the configuration for appling authentication in our microservice securityfilterchain and then the authentication part is automatically handle by spring as it will automatically use the JWtFilter and JwtUtil class 

as  we write the line: .anyRequest().authenticated() in our microservice securityFilterChain configuration Which apply the authentication : and it means that for any request coming to this microservice it will first check the authentication and then allow. 
as that line above conforms that we have to apply authentication so line

".addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);" in  our microservice securityFilterChain configuration  
use the jwtFilter from the common security module for authentication.

as we are using a centralized "common-security" module
No token generation ,NO Authentication code  and even No Authorization code is written in this microservice.


Authservice also Act as Kafka Event Producer : As it send the user details to the Kafka topic : "user.registered" after successful registration, which can be consumed by other services like UserService for there Use in that case.

for sending the USer data to Kafka topic  we are using the events(they are basically DTOs inside shared-events modules)  that are shared by all the microservices.

---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
version2: