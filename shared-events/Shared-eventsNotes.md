**shared-events**

shared-events is a small Java module that contains only **event** classes (DTOs) used for communication between microservices.
this module not a Spring Boot application(so not a microservice also).actually  it jyst a module built by right click on the project and select "New -> Module".


Example of events DTOs:UserRegisteredEvent (used in Authservice just after  we save the signup user by Event producer, also UserService use it by Event consumer to create the user profile in UserService database), UserDeletedEvent (used in Authservice just after we delete the user by Event producer, also In UserService it is used by Event consumer)

User registers
  │
  ▼
AuthService publishes event
  │   register to Kafka topic using UserRegisteredEvent DTO
  ▼
Kafka topic: user.registered
  │   
  ▼
UserService consumes event
  │   
  ▼
 from Kafka topic using UserRegisteredEvent DTO

Why We need shared-events module?
because Without **shared-events**: 

AuthService defines its own UserRegisteredEvent
UserService defines its own UserRegisteredEvent
Risk:
1. Different fields , Different naming so Runtime deserialization errors
2. Code duplication and maintenance issues

**shared-events** 
1. structure and how to use it:

we use structure
shared-events
├── pom.xml
└── src
     └── main
          └── java
                └── com.project.events
                         ├── UserRegisteredEvent
                         ├── UserDeletedEvent (eg)
                         ├── TicketCreatedEvent (eg)
                         └── TicketResolvedEvent (eg)


a. no controller,no service layer ,so buisness logic is not here, just simple DTOs with fields and getters/setters.
b.In pom file here we just define 
   Lombok → reduces boilerplate 
    Jackson → for JSON serialization/deserialization
as Producer → serialize DTO → Kafka (bytes) → Consumer → deserialize → DTO

How To user These DTOs in any microservice?
1. Add shared-events as a dependency in the microservice's pom.xml
  eg: can see we add the dependency in AuthService and UserService pom.xml
  <!-- For using the Event (DTO) Form Shared Events module   -->
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>shared-events</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
2. Import the event class in the microservice code where we want to use it
  eg: in AuthService we import UserRegisteredEvent   to publish events, also in UserService we import the same events to consume them.
   **import com.project.events.UserRegisteredEvent;**



Benifits of using this structure:
1. Single Source of Truth :each Event definitions exist in one place only.
2. Type Safety :Because Java classes are shared
3. Loose Coupling:Services do not depend on each other's implementation.
4. Better Microservice Communication:Instead of REST calls everywhere for inter-service communication, we can use events which are more efficient and decoupled.
5. Easier Maintenance:Changes to event structure are made in one place and reflected across all services.

**when we Use this Event-Driven Architecture Kafka:**
when a service needs to notify others
**asynchronous communication** is needed